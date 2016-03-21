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

#include <time.h>	// for clock()
#include "kissfftFloatWrapper.h"
#include <limits.h>

// logging...
#include <string.h>
#include <android/log.h>
#define LOG_TAG "kissfftFloat"
#define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, fmt, ##args)
#define LOGD(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, fmt, ##args)
#define LOGE(fmt, args...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, ##args)

#define SHRT_MIN_FLOAT ((float) SHRT_MIN)
#define SHRT_MAX_FLOAT ((float) SHRT_MAX)

kiss_fastfir_cfg cfgL = NULL;
kiss_fastfir_cfg cfgR = NULL;
jfloat *outArrFloatL = NULL;
jfloat *outArrFloatR = NULL;
jfloat *inArrFloat = NULL;
jfloat *audioMixAndFadeBufferL = NULL;
jfloat *audioMixAndFadeBufferR = NULL;
jfloat *crossfadeSaveBufferL = NULL;
jfloat *crossfadeSaveBufferR = NULL;
jfloat *crossfadeInTable = NULL;
jfloat *crossfadeOutTable = NULL;
bool filterInitialized = false;
jint filterOverlapInSamples = 0;
jint filterOverlapInBytes = 0;
jint fftPaddingInSamples = 0;
jint fftPaddingInBytes = 0;
jint fftSizeInSamples = 0;
jint fftSizeInBytes = 0;
jint crossfadeOverlapInSamples = 0;
jint crossfadeOverlapInBytes = 0;
jint audioMixAndFadeBufferSizeInSamples = 0;
jint audioMixAndFadeBufferSizeInBytes = 0;

void freeMem() {
	if (inArrFloat != NULL) free(inArrFloat);
	if (outArrFloatL != NULL) free(outArrFloatL);
	if (outArrFloatR != NULL) free(outArrFloatR);
	if (cfgL != NULL) free(cfgL);
	if (cfgR != NULL) free(cfgR);
	if (audioMixAndFadeBufferL != NULL) free(audioMixAndFadeBufferL);
	if (audioMixAndFadeBufferR != NULL) free(audioMixAndFadeBufferR);
	if (crossfadeSaveBufferL != NULL) free(crossfadeSaveBufferL);
	if (crossfadeSaveBufferR != NULL) free(crossfadeSaveBufferR);
	if (crossfadeInTable != NULL) free(crossfadeInTable);
	if (crossfadeOutTable != NULL) free(crossfadeOutTable);

	cfgL = NULL;
	cfgR = NULL;
	outArrFloatL = NULL;
	outArrFloatR = NULL;
	inArrFloat = NULL;
	audioMixAndFadeBufferL = NULL;
	audioMixAndFadeBufferR = NULL;
	crossfadeSaveBufferL = NULL;
	crossfadeSaveBufferR = NULL;
	crossfadeInTable = NULL;
	crossfadeOutTable = NULL;
}

JNIEXPORT jint JNICALL Java_net_ptrbrtz_adbs_FirFilter_kissfftCreate(JNIEnv *env, jobject thiz, jobjectArray hrirsL, jobjectArray hrirsR, jint fftSize, jint crossfadeOverlapSize, jint fftPaddingSize) {
	jfloatArray hrirCoeffsL, hrirCoeffsR;
	jint numCoeffFrames;
	jfloat *coeffsArrL = NULL;
	jfloat *coeffsArrR = NULL;

	filterInitialized = false;

	// free memory if any allocated already
	freeMem();

	// get hrir array sizes
	int numHrirs = env->GetArrayLength(hrirsL);
	if (numHrirs == 0) return -1;
	hrirCoeffsL = (jfloatArray) env->GetObjectArrayElement(hrirsL, 0);
	numCoeffFrames = env->GetArrayLength(hrirCoeffsL);
	if (numCoeffFrames == 0) return -1;

	// init sizes
	fftPaddingInSamples = fftPaddingSize;
	fftPaddingInBytes = fftPaddingSize * sizeof(jfloat);
	fftSizeInSamples = fftSize;
	fftSizeInBytes = fftSize * sizeof(jfloat);
	filterOverlapInSamples = numCoeffFrames - 1;
	filterOverlapInBytes = filterOverlapInBytes * sizeof(jfloat);
	crossfadeOverlapInSamples = crossfadeOverlapSize;
	crossfadeOverlapInBytes = crossfadeOverlapInSamples * sizeof(jfloat);
	audioMixAndFadeBufferSizeInSamples = fftSizeInSamples - filterOverlapInSamples - fftPaddingInSamples;
	audioMixAndFadeBufferSizeInBytes = audioMixAndFadeBufferSizeInSamples * sizeof(jfloat);

	// allocate temporary native memory to hold hrirs
	coeffsArrL = (jfloat*) malloc(numCoeffFrames * numHrirs * sizeof(jfloat));
	coeffsArrR = (jfloat*) malloc(numCoeffFrames * numHrirs * sizeof(jfloat));
	if (coeffsArrL == NULL || coeffsArrR == NULL) return -1;

	// copy and reorder hrirs to native arrays
	for (int i = 0; i < numHrirs; i++) {
		hrirCoeffsL = (jfloatArray) env->GetObjectArrayElement(hrirsL, i);
		hrirCoeffsR = (jfloatArray) env->GetObjectArrayElement(hrirsR, i);
		jfloat *hrirCoeffsElementsL = env->GetFloatArrayElements(hrirCoeffsL, 0);
		jfloat *hrirCoeffsElementsR = env->GetFloatArrayElements(hrirCoeffsR, 0);
		for (int j = 0; j < numCoeffFrames; j++) {
			coeffsArrL[i*numCoeffFrames + j] = hrirCoeffsElementsL[j];
			coeffsArrR[i*numCoeffFrames + j] = hrirCoeffsElementsR[j];
		}
		env->ReleaseFloatArrayElements(hrirCoeffsL, hrirCoeffsElementsL, 0);
		env->ReleaseFloatArrayElements(hrirCoeffsR, hrirCoeffsElementsR, 0);
		env->DeleteLocalRef(hrirCoeffsL);
		env->DeleteLocalRef(hrirCoeffsR);
	}

	// init kissfft fastfir
	size_t kissFftSize = fftSize;
	cfgL = kiss_fastfir_alloc(coeffsArrL, numHrirs, numCoeffFrames, &kissFftSize, 0, 0);
	if (kissFftSize != fftSize) return -1;
	kissFftSize = fftSize;
	cfgR = kiss_fastfir_alloc(coeffsArrR, numHrirs, numCoeffFrames, &kissFftSize, 0, 0);
	if (kissFftSize != fftSize) return -1;
	if (cfgL == NULL || cfgR == NULL) return -1;

	// free temporary native hrir arrays
	free(coeffsArrL);
	free(coeffsArrR);

	// alloc float fft input/output arrays
	inArrFloat = (jfloat*) malloc(fftSizeInBytes);
	outArrFloatL = (jfloat*) malloc(fftSizeInBytes);
	outArrFloatR = (jfloat*) malloc(fftSizeInBytes);
	if (inArrFloat == NULL || outArrFloatL == NULL || outArrFloatR == NULL) return -1;

	// set input array to zeros, so that the untouched part at the end (fft padding) is all zeros
	// (just to be sure...)
	memset(inArrFloat, 0, fftSizeInBytes);

	// alloc audio mixing and crossfade save buffer
	audioMixAndFadeBufferL = (jfloat*) malloc(audioMixAndFadeBufferSizeInBytes);
	audioMixAndFadeBufferR = (jfloat*) malloc(audioMixAndFadeBufferSizeInBytes);
	if (audioMixAndFadeBufferL == NULL || audioMixAndFadeBufferR == NULL) return -1;
	crossfadeSaveBufferL = (jfloat*) malloc(crossfadeOverlapInBytes);
	crossfadeSaveBufferR = (jfloat*) malloc(crossfadeOverlapInBytes);
	if (crossfadeSaveBufferL == NULL || crossfadeSaveBufferR == NULL) return -1;

	// set audio mix buffers to zeros
	// (they are reset each run, but before that the crossfade save
	// buffers are copied from there - run 0 would be undefinded)
	memset(audioMixAndFadeBufferL, 0, audioMixAndFadeBufferSizeInBytes);
	memset(audioMixAndFadeBufferR, 0, audioMixAndFadeBufferSizeInBytes);

	// alloc and init crossfade lookup tables
	crossfadeInTable = (jfloat*) malloc(crossfadeOverlapInBytes);
	crossfadeOutTable = (jfloat*) malloc(crossfadeOverlapInBytes);
	if (crossfadeInTable == NULL || crossfadeOutTable == NULL) return -1;
	for (int i = 0; i < crossfadeOverlapInSamples; ++i) { // cosine
		crossfadeOutTable[i] = cos((float) i / (float) (crossfadeOverlapInSamples - 1) * M_PI) / 2.0f + 0.5f;
		crossfadeInTable[i] = 1.0f - crossfadeOutTable[i];
	}
	// TODO remove
	/*for (int i = 0; i < crossfadeOverlapInSamples; ++i) {	//linear
		crossfadeOutTable[i] = 1.0f - ((float) i / (float) (crossfadeOverlapInSamples - 1));
		crossfadeInTable[i] = 1.0f - crossfadeOutTable[i];
	}*/

	// ready to go
	filterInitialized = true;
	return 0;
}

JNIEXPORT jint JNICALL Java_net_ptrbrtz_adbs_FirFilter_kissfftDestroy(JNIEnv *env, jobject thiz) {
	freeMem();
	filterInitialized = false;
	return 0;
}

JNIEXPORT jint JNICALL Java_net_ptrbrtz_adbs_FirFilter_beginRenderingBlock(JNIEnv *env, jobject thiz) {
	// save crossfade samples
	memcpy(crossfadeSaveBufferL, audioMixAndFadeBufferL + (audioMixAndFadeBufferSizeInSamples - crossfadeOverlapInSamples), crossfadeOverlapInBytes);
	memcpy(crossfadeSaveBufferR, audioMixAndFadeBufferR + (audioMixAndFadeBufferSizeInSamples - crossfadeOverlapInSamples), crossfadeOverlapInBytes);

	// clear audio mixing buffers
	memset(audioMixAndFadeBufferL, 0, audioMixAndFadeBufferSizeInBytes);
	memset(audioMixAndFadeBufferR, 0, audioMixAndFadeBufferSizeInBytes);

	return 0;
}

JNIEXPORT jint JNICALL Java_net_ptrbrtz_adbs_FirFilter_endRenderingBlock(JNIEnv *env, jobject thiz, jboolean crossfade, jbyteArray out) {
	jshort *outArr;

	// TODO delete
	// duplicate L channel to R for x-fade testing
	//memcpy(audioMixAndFadeBufferR, audioMixAndFadeBufferL, audioMixAndFadeBufferSizeInBytes);

	// do crossfade
	if (crossfade) {
		for (int i = 0; i < crossfadeOverlapInSamples; ++i) {
			audioMixAndFadeBufferL[i] = crossfadeInTable[i] * audioMixAndFadeBufferL[i] + crossfadeOutTable[i] * crossfadeSaveBufferL[i];
			audioMixAndFadeBufferR[i] = crossfadeInTable[i] * audioMixAndFadeBufferR[i] + crossfadeOutTable[i] * crossfadeSaveBufferR[i];
		}
	}

	// mount output array
	outArr = (jshort*) env->GetPrimitiveArrayCritical(out, NULL);
	if (outArr == NULL) return -1;

	// convert final mixed and crossfaded output to short
	for (int i = 0, j = 0; i < audioMixAndFadeBufferSizeInSamples - crossfadeOverlapInSamples; i++, j += 2) {
		// using min/max check instead of just converting like this:
		//outArr[j] = (jshort) (audioMixAndFadeBufferL[i]);
		//outArr[j+1] = (jshort) (audioMixAndFadeBufferR[i]);
		outArr[j] = (audioMixAndFadeBufferL[i] > SHRT_MAX_FLOAT) ? SHRT_MAX : ((audioMixAndFadeBufferL[i] < SHRT_MIN_FLOAT) ? SHRT_MIN : (jshort) audioMixAndFadeBufferL[i]);
		outArr[j+1] = (audioMixAndFadeBufferR[i] > SHRT_MAX_FLOAT) ? SHRT_MAX : ((audioMixAndFadeBufferR[i] < SHRT_MIN_FLOAT) ? SHRT_MIN : (jshort) audioMixAndFadeBufferR[i]);
	}

	// unmount output array
	env->ReleasePrimitiveArrayCritical(out, outArr, 0);

	return 0;
}

JNIEXPORT jint JNICALL Java_net_ptrbrtz_adbs_FirFilter_kissfftFilter(JNIEnv *env, jobject thiz, jbyteArray in, jint hrtfIndex, jfloat sampleScaling) {
	jshort *inArr;

	// check if setup was done
	if (!filterInitialized) return -1;

	// mount input array
	inArr = (jshort*) env->GetPrimitiveArrayCritical(in, NULL);
	if (inArr == NULL) return -1;

	// convert input to float
	for (int i = 0; i < fftSizeInSamples - fftPaddingInSamples; i++) {
		inArrFloat[i] = (jfloat) inArr[i];
	}

	// unmount input array
	env->ReleasePrimitiveArrayCritical(in, inArr, JNI_ABORT);

	// set hrtfs to be used by convolution
	cfgL->current_fir_freq_resp = hrtfIndex;
	cfgR->current_fir_freq_resp = hrtfIndex;

	// do convolution
	// input and output arrays have to have same size (although last samples of output are garbage)
	fastconv1buf(cfgL, (kffsamp_t*)inArrFloat, (kffsamp_t*)outArrFloatL);	// left
	fastconv1buf(cfgR, (kffsamp_t*)inArrFloat, (kffsamp_t*)outArrFloatR);	// right

	int numGoodSamples = cfgL->ngood - fftPaddingInSamples;
	// scale samples and add to mixing buffer
	for (int i = 0; i < numGoodSamples; i++) {
		audioMixAndFadeBufferL[i] += outArrFloatL[i] * sampleScaling;
		audioMixAndFadeBufferR[i] += outArrFloatR[i] * sampleScaling;
	}

	if (numGoodSamples != audioMixAndFadeBufferSizeInSamples) return -1;
	else return numGoodSamples;
}

JNIEXPORT jlong JNICALL Java_net_ptrbrtz_adbs_FirFilter_jniNanoTime(JNIEnv *env, jobject thiz) {
	struct timespec res;
	clock_gettime(CLOCK_THREAD_CPUTIME_ID, &res);
	return 1000000000 * (long)res.tv_sec + res.tv_nsec;
}
