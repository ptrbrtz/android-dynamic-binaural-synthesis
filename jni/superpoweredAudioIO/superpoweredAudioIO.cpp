/*
Copyright (c) 2016 Peter Bartz

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

#include "superpoweredAudioIO.h"

#include <stdlib.h>
#include <queue>
#include <pthread.h>
#include <stdio.h>
#include <android/log.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_AndroidConfiguration.h>

#include "../SuperpoweredSDK/Superpowered/SuperpoweredAndroidAudioIO.h"
#include "../SuperpoweredSDK/Superpowered/SuperpoweredResampler.h"
#include "BlockingByteBufferQueue.h"

#define LOG_TAG "superpoweredAudioIO"
#define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, fmt, ##args)
#define LOGD(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, fmt, ##args)
#define LOGE(fmt, args...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, ##args)

using namespace std;

/*
 * This whole code should only be used as a single audio sink by one thread.
 * Thread safety is not checked for multiple threads.
 */

int internalBufSizeInFrames;
int resampledInternalBufSizeInFrames;
int outputBufSizeInFrames;
int ringBufSizeMultiplier;
SuperpoweredAndroidAudioIO *audioSystem;
SuperpoweredResampler *resampler;
short *resampleInBuf;
float *resampleTmpBuf;
pthread_mutex_t pauseMutex;
pthread_cond_t pauseCond;
bool pauseMutexInitialized = false;
volatile bool playing;

BlockingByteBufferQueue *emptyBufsQueue;
char *partialBuf;
int partialBufMarkInFrames;
BlockingByteBufferQueue *fullBufsQueue;
queue<char *> allBufsQueue;	// holds sum of both queues (makes it easier to free all buffers)

JNIEXPORT void JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_enqueueSamples(JNIEnv *env, jobject thiz,
		jbyteArray buf, jint numFrames) {

	// mount java array
	char *inputBuf = (char*) env->GetPrimitiveArrayCritical(buf, NULL);

	// enqueue
	int remainingFramesToCopy = numFrames;
	int alreadyCopiedFrames;
	while (remainingFramesToCopy > 0) {
		alreadyCopiedFrames = numFrames - remainingFramesToCopy;

		if (partialBufMarkInFrames > 0) {	// continue to fill partial buffer?
			int copyFrames = min(outputBufSizeInFrames - partialBufMarkInFrames, remainingFramesToCopy);
			memcpy(partialBuf + partialBufMarkInFrames * 4, inputBuf + alreadyCopiedFrames * 4, copyFrames * 4);
			partialBufMarkInFrames += copyFrames;
			if (partialBufMarkInFrames == outputBufSizeInFrames) {	// partial buffer is full now
				fullBufsQueue->put(partialBuf);	// might block
				partialBufMarkInFrames = 0;
			}
			remainingFramesToCopy -= copyFrames;
		} else if (remainingFramesToCopy >= outputBufSizeInFrames) {	// fill whole new buffer?
			// get empty buffer
			char *outBuf = emptyBufsQueue->take(); // might block
			memcpy(outBuf, inputBuf + alreadyCopiedFrames * 4, outputBufSizeInFrames * 4);
			fullBufsQueue->put(outBuf);	// might block
			remainingFramesToCopy -= outputBufSizeInFrames;
		} else {	// start to partially fill new bufffer?
			partialBuf = emptyBufsQueue->take(); // might block
			memcpy(partialBuf, inputBuf + alreadyCopiedFrames * 4, remainingFramesToCopy * 4);
			partialBufMarkInFrames = remainingFramesToCopy;
			remainingFramesToCopy = 0;
		}
	}

	env->ReleasePrimitiveArrayCritical(buf, inputBuf, JNI_ABORT);
}

static bool audioCallback(void *clientdata, short int *audioIO, int numberOfSamples, int samplerate) {
	if (!playing) return false;

	// take() might block
	char *outBuf = fullBufsQueue->take();

	// check if we received "synchronized pause" signal aka NULL
	if (outBuf == NULL) {
		pthread_mutex_lock(&pauseMutex);
		playing = false;
		pthread_cond_signal(&pauseCond);
		pthread_mutex_unlock(&pauseMutex);
		return false;
	}

	// copy to audio system buffer and save empty buffer
	memcpy(audioIO, outBuf, outputBufSizeInFrames * 4);
	emptyBufsQueue->put(outBuf);

	return true;
}

JNIEXPORT jboolean JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_setupSuperpowered(JNIEnv *env, jobject thiz,
		jint _internalBufSizeInFrames, jint _resampledInternalBufSizeInFrames, jint _outputBufSizeInFrames,
		jint internalSamplerate, jint outputSamplerate, jint _ringBufSizeMultiplier) {

	internalBufSizeInFrames = _internalBufSizeInFrames;
	resampledInternalBufSizeInFrames = (int) _resampledInternalBufSizeInFrames;
	outputBufSizeInFrames = (int) _outputBufSizeInFrames;
	ringBufSizeMultiplier = (int) _ringBufSizeMultiplier;
	emptyBufsQueue = new BlockingByteBufferQueue(_ringBufSizeMultiplier);
	partialBufMarkInFrames = 0;
	fullBufsQueue = new BlockingByteBufferQueue(_ringBufSizeMultiplier);
	for (int i = 0; i < ringBufSizeMultiplier; i++) {
		char *newBuf = (char*) malloc(outputBufSizeInFrames * 4);
		emptyBufsQueue->put(newBuf);
		allBufsQueue.push(newBuf);
	}

	// setup resampler
	resampler = new SuperpoweredResampler();
	resampleInBuf = (short*) malloc((internalBufSizeInFrames * 2 + 64) * sizeof(short)); // + 64: see Superpowered docs
	resampleTmpBuf = (float*) malloc((internalBufSizeInFrames * 2 + 64) * sizeof(float)); // + 64: see Superpowered docs
	resampler->rate = (double) internalSamplerate / (double) outputSamplerate;

	// init synch stuff
	if (!pauseMutexInitialized) {
		pthread_mutex_init(&pauseMutex, NULL);
		pthread_cond_init(&pauseCond, NULL);
		pauseMutexInitialized = true;
	}

	audioSystem = new SuperpoweredAndroidAudioIO(outputSamplerate, outputBufSizeInFrames, false, true, audioCallback, 0, -1, SL_ANDROID_STREAM_MEDIA, 0);
	playing = true;

	return true;
}

void synchPause() {
	if (playing) {
		pthread_mutex_lock(&pauseMutex);
		// signal synchronized pause
		fullBufsQueue->putSpecialFront(NULL); // put marker in front of queue
		while (playing)
			pthread_cond_wait(&pauseCond, &pauseMutex);
		pthread_mutex_unlock(&pauseMutex);
	}
}

void synchFlushAndPause() {
	if (!playing) return;

	// flush partial buffer?
	if (partialBufMarkInFrames > 0) {	// continue to fill partial buffer?
		memset(partialBuf + partialBufMarkInFrames * 4, 0, (outputBufSizeInFrames - partialBufMarkInFrames) * 4);
		fullBufsQueue->put(partialBuf);	// might block
		partialBufMarkInFrames = 0;
	}

	pthread_mutex_lock(&pauseMutex);
	// signal synchronized pause
	fullBufsQueue->putSpecial(NULL); // put marker at the end of queue
	while (playing)
		pthread_cond_wait(&pauseCond, &pauseMutex);
	pthread_mutex_unlock(&pauseMutex);
}

JNIEXPORT void JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_shutdownSuperpowered(JNIEnv *env, jobject thiz) {
	// pause
	synchPause();

	// safely shutdown and free mem
	audioSystem->stop();
    delete audioSystem;
	delete resampler;
	delete fullBufsQueue;
	delete emptyBufsQueue;
    free(resampleInBuf);
    free(resampleTmpBuf);
	while (allBufsQueue.size() > 0) {
		free(allBufsQueue.front());
		allBufsQueue.pop();
	}
}

JNIEXPORT int JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_resample(JNIEnv *env, jobject thiz, jbyteArray input, jbyteArray output) {
	jshort *inputArr = (jshort*) env->GetPrimitiveArrayCritical(input, NULL);
	memcpy(resampleInBuf, inputArr, internalBufSizeInFrames * 4);
	env->ReleasePrimitiveArrayCritical(input, inputArr, JNI_ABORT);

	jshort *outputArr = (jshort*) env->GetPrimitiveArrayCritical(output, NULL);
	int numResampledSamples = resampler->process(resampleInBuf, resampleTmpBuf, outputArr, internalBufSizeInFrames);
	env->ReleasePrimitiveArrayCritical(output, (jbyte*)outputArr, 0);
	return numResampledSamples;
}

JNIEXPORT void JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_nativePlay(JNIEnv *env, jobject thiz, jboolean clearRingBuf) {
	if (clearRingBuf) {
		for (int i; i < fullBufsQueue->getNumElems(); i++)
			emptyBufsQueue->put(fullBufsQueue->take());
	}
	if (partialBufMarkInFrames > 0) { // partial buffer is in use
		emptyBufsQueue->put(partialBuf);
		partialBufMarkInFrames = 0;
	}
	playing = true;
}

JNIEXPORT void JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_nativePause(JNIEnv *env, jobject thiz) {
	synchPause();
}

JNIEXPORT void JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_nativeFlushAndPause(JNIEnv *env, jobject thiz) {
	synchFlushAndPause();
}

JNIEXPORT void JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_linearFade(JNIEnv *env, jobject thiz, jbyteArray buf,
		jint numFrames, int sign) {
	jshort *bufArr = (jshort*) env->GetPrimitiveArrayCritical(buf, NULL);

	float currentScaling = 0.0f;
	float scalingIncrement = 1.0f / (float) numFrames;
	if (sign < 0) {
		currentScaling = 1.0f;
		scalingIncrement *= -1.0f;
	}
	for (int i = 0; i < numFrames * 2; i += 2) {
		bufArr[i] = round((float) bufArr[i] * currentScaling);
		bufArr[i+1] = round((float) bufArr[i+1] * currentScaling);
		currentScaling += scalingIncrement;
	}

	env->ReleasePrimitiveArrayCritical(buf, bufArr, 0);
}

