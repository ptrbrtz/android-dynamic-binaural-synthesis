APP_PROJECT_PATH	:= $(call my-dir/..)
APP_OPTIM			:= release
APP_PLATFORM		:= android-9

# We have to explicitly compile for v7a abi, to make float run fast (use FPU) on e.g. Samsung Galaxy S
# We're not compiling for standard "armeabi", because Superpowered SDK does not support it
APP_ABI				:= armeabi-v7a

APP_STL := stlport_shared