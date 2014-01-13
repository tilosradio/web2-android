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

#ifndef MEDIAMETADATARETRIEVER_H
#define MEDIAMETADATARETRIEVER_H

extern "C" {
	#include "libavcodec/avcodec.h"
	#include "libavformat/avformat.h"
    #include "ffmpeg_mediametadataretriever.h"
}

using namespace std;

class MediaMetadataRetriever
{
	State* state;
public:
    MediaMetadataRetriever();
    ~MediaMetadataRetriever();
    void disconnect();
    int setDataSource(const char* dataSourceUrl);
    AVPacket* getFrameAtTime(int64_t timeUs);
    AVPacket* extractAlbumArt();
    const char* extractMetadata(const char* key);
};

#endif // MEDIAMETADATARETRIEVER_H
