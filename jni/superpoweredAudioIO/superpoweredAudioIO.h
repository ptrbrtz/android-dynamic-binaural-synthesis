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

#include <jni.h>

#ifndef __SUPERPOWEREDAUDIOIO_H__
#define __SUPERPOWEREDAUDIOIO_H__

#ifdef __cplusplus 
extern "C" { 
#endif
JNIEXPORT jboolean JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_setupSuperpowered(JNIEnv *env, jobject thiz,
		jint _internalBufSizeInFrames, jint _resampledInternalBufSizeInFrames, jint _outputBufSizeInFrames,
		jint internalSamplerate, jint outputSamplerate, jint _ringBufSizeMultiplier);
JNIEXPORT void JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_shutdownSuperpowered(JNIEnv *env, jobject thiz);
JNIEXPORT int JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_resample(JNIEnv *env, jobject thiz, jbyteArray input,
		jbyteArray output);
JNIEXPORT void JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_nativePlay(JNIEnv *env, jobject thiz, jboolean clearRingBuf);
JNIEXPORT void JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_nativePause(JNIEnv *env, jobject thiz);
JNIEXPORT void JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_nativeFlushAndPause(JNIEnv *env, jobject thiz);
JNIEXPORT void JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_linearFade(JNIEnv *env, jobject thiz, jbyteArray buf,
		jint numFrames, jint sign);
JNIEXPORT void JNICALL Java_net_ptrbrtz_adbs_SuperpoweredAudioIO_enqueueSamples(JNIEnv *env, jobject thiz,
		jbyteArray jBuf, jint numFrames);

#ifdef __cplusplus 
} 
#endif

#endif
