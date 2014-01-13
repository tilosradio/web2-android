/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2013 William Seemann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "MediaMetadataRetrieverJNI"

#include <assert.h>
#include <android/log.h>
#include <mediametadataretriever.h>
#include "jni.h"

extern "C" {
	#include "libavcodec/avcodec.h"
	#include "libavformat/avformat.h"
}

using namespace std;

struct fields_t {
    jfieldID context;
};

static fields_t fields;
static const char* const kClassPathName = "net/sourceforge/servestream/media/MediaMetadataRetriever";

void jniThrowException(JNIEnv* env, const char* className,
    const char* msg) {
    jclass exception = env->FindClass(className);
    env->ThrowNew(exception, msg);
}

static void process_media_retriever_call(JNIEnv *env, int opStatus, const char* exception, const char *message)
{
    if (opStatus == -2) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
    } else if (opStatus == -1) {
        if (strlen(message) > 230) {
            // If the message is too long, don't bother displaying the status code.
            jniThrowException( env, exception, message);
        } else {
            char msg[256];
            // Append the status code to the message.
            sprintf(msg, "%s: status = 0x%X", message, opStatus);
            jniThrowException( env, exception, msg);
        }
    }
}

static MediaMetadataRetriever* getRetriever(JNIEnv* env, jobject thiz)
{
    // No lock is needed, since it is called internally by other methods that are protected
    MediaMetadataRetriever* retriever = (MediaMetadataRetriever*) env->GetIntField(thiz, fields.context);
    return retriever;
}

static void setRetriever(JNIEnv* env, jobject thiz, int retriever)
{
    // No lock is needed, since it is called internally by other methods that are protected
    MediaMetadataRetriever *old = (MediaMetadataRetriever*) env->GetIntField(thiz, fields.context);
    env->SetIntField(thiz, fields.context, retriever);
}

extern "C" JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_setDataSource(JNIEnv *env, jobject thiz, jstring path) {
	//__android_log_write(ANDROID_LOG_INFO, LOG_TAG, "setDataSource");
    MediaMetadataRetriever* retriever = getRetriever(env, thiz);
    if (retriever == 0) {
        jniThrowException(env, "java/lang/IllegalStateException", "No retriever available");
        return;
    }

    if (!path) {
        jniThrowException(env, "java/lang/IllegalArgumentException", "Null pointer");
        return;
    }

    const char *tmp = env->GetStringUTFChars(path, NULL);
    if (!tmp) {  // OutOfMemoryError exception already thrown
        return;
    }

    // Don't let somebody trick us in to reading some random block of memory
    if (strncmp("mem://", tmp, 6) == 0) {
        jniThrowException(env, "java/lang/IllegalArgumentException", "Invalid pathname");
        return;
    }

    // Workaround for FFmpeg ticket #998
    // "must convert mms://... streams to mmsh://... for FFmpeg to work"
    char *restrict_to = strstr(tmp, "mms://");
    if (restrict_to) {
    	strncpy(restrict_to, "mmsh://", 6);
    	puts(tmp);
    }

    process_media_retriever_call(
            env,
            retriever->setDataSource(tmp),
            "java/lang/IllegalArgumentException",
            "setDataSource failed");

    env->ReleaseStringUTFChars(path, tmp);
    tmp = NULL;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever__1getFrameAtTime(JNIEnv *env, jobject thiz, jlong timeUs)
{
   //__android_log_write(ANDROID_LOG_INFO, LOG_TAG, "getFrameAtTime");
   MediaMetadataRetriever* retriever = getRetriever(env, thiz);
   if (retriever == 0) {
       jniThrowException(env, "java/lang/IllegalStateException", "No retriever available");
       return NULL;
   }

   AVPacket* packet = retriever->getFrameAtTime(timeUs);
   jbyteArray array = NULL;

   if (packet) {
	   int size = packet->size;
	   uint8_t* data = packet->data;
	   array = env->NewByteArray(size);
	   if (!array) {  // OutOfMemoryError exception has already been thrown.
		   __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "getFrameAtTime: OutOfMemoryError is thrown.");
       } else {
       	   //__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "getFrameAtTime: Got frame.");
    	   jbyte* bytes = env->GetByteArrayElements(array, NULL);
           if (bytes != NULL) {
        	   memcpy(bytes, data, size);
               env->ReleaseByteArrayElements(array, bytes, 0);
           }
       }

	   av_free_packet(packet);
   }

   return array;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_getEmbeddedPicture(JNIEnv *env, jobject thiz)
{
   //__android_log_write(ANDROID_LOG_INFO, LOG_TAG, "getEmbeddedPicture");
   MediaMetadataRetriever* retriever = getRetriever(env, thiz);
   if (retriever == 0) {
       jniThrowException(env, "java/lang/IllegalStateException", "No retriever available");
       return NULL;
   }

   AVPacket* packet = retriever->extractAlbumArt();
   jbyteArray array = NULL;

   if (packet) {
	   int size = packet->size;
	   uint8_t* data = packet->data;
	   array = env->NewByteArray(size);
	   if (!array) {  // OutOfMemoryError exception has already been thrown.
		   //__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "getEmbeddedPicture: OutOfMemoryError is thrown.");
       } else {
       	   //__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "getEmbeddedPicture: Found album art.");
    	   jbyte* bytes = env->GetByteArrayElements(array, NULL);
           if (bytes != NULL) {
        	   memcpy(bytes, data, size);
               env->ReleaseByteArrayElements(array, bytes, 0);
           }
       }

	   av_free_packet(packet);
   }

   return array;
}

extern "C" JNIEXPORT jobject JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_extractMetadata(JNIEnv *env, jobject thiz, jstring jkey)
{
	//__android_log_write(ANDROID_LOG_INFO, LOG_TAG, "extractMetadata");
    MediaMetadataRetriever* retriever = getRetriever(env, thiz);
    if (retriever == 0) {
        jniThrowException(env, "java/lang/IllegalStateException", "No retriever available");
        return NULL;
    }

    if (!jkey) {
        jniThrowException(env, "java/lang/IllegalArgumentException", "Null pointer");
        return NULL;
    }

    const char *key = env->GetStringUTFChars(jkey, NULL);
    if (!key) {  // OutOfMemoryError exception already thrown
        return NULL;
    }

    const char* value = retriever->extractMetadata(key);
    if (!value) {
    	//__android_log_write(ANDROID_LOG_INFO, LOG_TAG, "extractMetadata: Metadata is not found");
        return NULL;
    }
    //__android_log_print(ANDROID_LOG_INFO, LOG_TAG, "extractMetadata: value (%s) for keyCode(%s)", value, key);
    env->ReleaseStringUTFChars(jkey, key);
    return env->NewStringUTF(value);
}

extern "C" JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_release(JNIEnv *env, jobject thiz)
{
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "release");
    //Mutex::Autolock lock(sLock);
    MediaMetadataRetriever* retriever = getRetriever(env, thiz);
    delete retriever;
    setRetriever(env, thiz, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_native_1finalize(JNIEnv *env, jobject thiz)
{
	//__android_log_write(ANDROID_LOG_INFO, LOG_TAG, "native_finalize");
    // No lock is needed, since Java_net_sourceforge_servestream_media_MediaMetadataRetriever_release() is protected
	Java_net_sourceforge_servestream_media_MediaMetadataRetriever_release(env, thiz);
}

extern "C" JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_native_1init(JNIEnv *env, jobject thiz)
{
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "native_init");
    jclass clazz = env->FindClass(kClassPathName);
    if (clazz == NULL) {
        return;
    }

    fields.context = env->GetFieldID(clazz, "mNativeContext", "I");
    if (fields.context == NULL) {
        return;
    }

    // Initialize libavformat and register all the muxers, demuxers and protocols.
	av_register_all();
	avformat_network_init();
}

extern "C" JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_native_1setup(JNIEnv *env, jobject thiz)
{
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "native_setup");
    MediaMetadataRetriever* retriever = new MediaMetadataRetriever();
    if (retriever == 0) {
        jniThrowException(env, "java/lang/RuntimeException", "Out of memory");
        return;
    }
    setRetriever(env, thiz, (int)retriever);
}
