LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libjackpal-androidterm5

LOCAL_SRC_FILES :=  common.cpp fileCompat.cpp termExec.cpp

LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog

include $(BUILD_SHARED_LIBRARY)
