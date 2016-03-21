LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# COMPILE FLAG EXPERIMENTS FOR FIR FILTER LIB
# very slow, forces no-FPU
#LOCAL_CFLAGS	:= -mfloat-abi=soft
# does seem to make it a bit slower
#LOCAL_CFLAGS	:= -mtune=cortex-a8 -mfpu=neon -ftree-vectorize -mfloat-abi=softfp

# doesn't seem to make it faster
# enabling NEON automatically also enables VFPv3-D32
#LOCAL_ARM_NEON := true
#LOCAL_CFLAGS   := -DHAVE_NEON=1

# doesn't seem to make it faster
#LOCAL_CFLAGS	:= -mfpu=vfpv3
#LOCAL_CFLAGS	:= -mfpu=vfpv3-d16-fp16

# doesn't seem to make it faster
#LOCAL_CFLAGS	+= -ffast-math
# doesnt seem to make it faster
#LOCAL_CFLAGS	+= -O3

### TEMP
#LOCAL_CFLAGS	:= -march=armv7-a -mfloat-abi=softfp -mfpu=vfp -Wl,--fix-cortex-a8 -mthumb # FPU (default for armeabi-v7a and armeabi-v7a-hard)
#LOCAL_CFLAGS	:= -march=armv7-a -mfloat-abi=soft -Wl,--fix-cortex-a8 -mthumb # force no FPU -> slow

# FIR FILTER
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog
LOCAL_SRC_FILES += \
	kissfft_floatingpoint/kiss_fft.c \
	kissfft_floatingpoint/kiss_fftr.c \
	kissfft_floatingpoint/kiss_fastfir.c \
	kissfft_floatingpoint/kissfftFloatWrapper.cpp
LOCAL_MODULE    := firfilter
LOCAL_CFLAGS = -O3
include $(BUILD_SHARED_LIBRARY)


# CPU INFO
include $(CLEAR_VARS)
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog
LOCAL_SRC_FILES += nativeCPUInfo/nativeCPUInfo.cpp
LOCAL_MODULE    := nativeCPUInfo
LOCAL_CFLAGS = -O3
include $(BUILD_SHARED_LIBRARY)


# SUPERPOWERED AUDIO
SUPERPOWERED_PATH := ../SuperpoweredSDK/Superpowered

include $(CLEAR_VARS)
LOCAL_MODULE := superpoweredSDK
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
	LOCAL_SRC_FILES := $(SUPERPOWERED_PATH)/libSuperpoweredAndroidARM.a
endif
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)  
LOCAL_MODULE := superpoweredAudioIO  

LOCAL_SRC_FILES := \
    superpoweredAudioIO/superpoweredAudioIO.cpp \
    $(SUPERPOWERED_PATH)/SuperpoweredAndroidAudioIO.cpp
LOCAL_C_INCLUDES += $(SUPERPOWERED_PATH)

LOCAL_LDLIBS := -llog -landroid -lOpenSLES 
LOCAL_STATIC_LIBRARIES := superpoweredSDK
LOCAL_CFLAGS = -O3
include $(BUILD_SHARED_LIBRARY)
