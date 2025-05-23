/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Test AAudio SessionId, which is used to associate Effects with a stream

#include <stdio.h>
#include <unistd.h>

#include <aaudio/AAudio.h>
#include <gtest/gtest.h>

#include "test_aaudio.h"
#include "utils.h"

constexpr int kNumFrames = 256;
constexpr int kChannelCount = 2;

// Test AAUDIO_SESSION_ID_NONE default
static void checkSessionIdNone(aaudio_performance_mode_t perfMode) {
    if (!deviceSupportsFeature(FEATURE_PLAYBACK)) return;

    std::unique_ptr<float[]> buffer(new float[kNumFrames * kChannelCount]);

    AAudioStreamBuilder *aaudioBuilder = nullptr;

    AAudioStream *aaudioStream1 = nullptr;
    int32_t       sessionId1 = 0;

    // Use an AAudioStreamBuilder to contain requested parameters.
    ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder));

    // Request stream properties.
    AAudioStreamBuilder_setPerformanceMode(aaudioBuilder, perfMode);

    // Create an AAudioStream using the Builder.
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream1));

    // Since we did not request or specify a SessionID, we should get NONE
    sessionId1 = AAudioStream_getSessionId(aaudioStream1);
    ASSERT_EQ(AAUDIO_SESSION_ID_NONE, sessionId1);

    ASSERT_EQ(AAUDIO_OK, AAudioStream_requestStart(aaudioStream1));

    ASSERT_EQ(kNumFrames, AAudioStream_write(aaudioStream1, buffer.get(), kNumFrames, NANOS_PER_SECOND));

    EXPECT_EQ(AAUDIO_OK, AAudioStream_requestStop(aaudioStream1));

    EXPECT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream1));
    AAudioStreamBuilder_delete(aaudioBuilder);
}

class AAudioTestSessionId : public AAudioCtsBase {};

TEST_F(AAudioTestSessionId, aaudio_session_id_none_perfnone) {
    checkSessionIdNone(AAUDIO_PERFORMANCE_MODE_NONE);
}

TEST_F(AAudioTestSessionId, aaudio_session_id_none_lowlat) {
    checkSessionIdNone(AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
}

// Test AAUDIO_SESSION_ID_ALLOCATE
static void checkSessionIdAllocate(aaudio_performance_mode_t perfMode,
                                   aaudio_direction_t direction) {
    // Since this test creates streams in both directions, it can't work
    // if either of them is not supported by the device.
    if (!deviceSupportsFeature(FEATURE_RECORDING)
            || !deviceSupportsFeature(FEATURE_PLAYBACK)) return;

    std::unique_ptr<float[]> buffer(new float[kNumFrames * kChannelCount]);

    AAudioStreamBuilder *aaudioBuilder = nullptr;

    AAudioStream *aaudioStream1 = nullptr;
    int32_t       sessionId1 = 0;
    AAudioStream *aaudioStream2 = nullptr;
    int32_t       sessionId2 = 0;

    // Use an AAudioStreamBuilder to contain requested parameters.
    ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder));

    // Request stream properties.
    AAudioStreamBuilder_setPerformanceMode(aaudioBuilder, perfMode);
    // This stream could be input or output.
    AAudioStreamBuilder_setDirection(aaudioBuilder, direction);

    // Ask AAudio to allocate a Session ID.
    AAudioStreamBuilder_setSessionId(aaudioBuilder, AAUDIO_SESSION_ID_ALLOCATE);

    // Create an AAudioStream using the Builder.
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream1));

    // Get the allocated ID from the stream.
    sessionId1 = AAudioStream_getSessionId(aaudioStream1);

    // Check for invalid session IDs.
    ASSERT_NE(AAUDIO_SESSION_ID_NONE, sessionId1);
    ASSERT_NE(AAUDIO_SESSION_ID_ALLOCATE, sessionId1);

    ASSERT_EQ(AAUDIO_OK, AAudioStream_requestStart(aaudioStream1));

    if (direction == AAUDIO_DIRECTION_INPUT) {
        ASSERT_EQ(kNumFrames, AAudioStream_read(aaudioStream1,
                                                buffer.get(), kNumFrames, NANOS_PER_SECOND));
    } else {
        ASSERT_EQ(kNumFrames, AAudioStream_write(aaudioStream1,
                                         buffer.get(), kNumFrames, NANOS_PER_SECOND));
    }

    EXPECT_EQ(AAUDIO_OK, AAudioStream_requestStop(aaudioStream1));

    // Now open a second stream using the same session ID. ==================
    AAudioStreamBuilder_setSessionId(aaudioBuilder, sessionId1);

    // Reverse direction for second stream.
    aaudio_direction_t otherDirection = (direction == AAUDIO_DIRECTION_OUTPUT)
                                        ? AAUDIO_DIRECTION_INPUT
                                        : AAUDIO_DIRECTION_OUTPUT;
    AAudioStreamBuilder_setDirection(aaudioBuilder, otherDirection);

    // Create an AAudioStream using the Builder.
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream2));

    // Get the allocated ID from the stream.
    // It should match the ID that we set it to in the builder.
    sessionId2 = AAudioStream_getSessionId(aaudioStream2);
    ASSERT_EQ(sessionId1, sessionId2);

    ASSERT_EQ(AAUDIO_OK, AAudioStream_requestStart(aaudioStream2));

    if (otherDirection == AAUDIO_DIRECTION_INPUT) {
        ASSERT_EQ(kNumFrames, AAudioStream_read(aaudioStream2,
                                                 buffer.get(), kNumFrames, NANOS_PER_SECOND));
    } else {
        ASSERT_EQ(kNumFrames, AAudioStream_write(aaudioStream2,
                                                 buffer.get(), kNumFrames, NANOS_PER_SECOND));
    }

    EXPECT_EQ(AAUDIO_OK, AAudioStream_requestStop(aaudioStream2));

    EXPECT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream2));


    EXPECT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream1));
    AAudioStreamBuilder_delete(aaudioBuilder);
}

TEST_F(AAudioTestSessionId, aaudio_session_id_alloc_perfnone_in) {
    checkSessionIdAllocate(AAUDIO_PERFORMANCE_MODE_NONE, AAUDIO_DIRECTION_INPUT);
}
TEST_F(AAudioTestSessionId, aaudio_session_id_alloc_perfnone_out) {
    checkSessionIdAllocate(AAUDIO_PERFORMANCE_MODE_NONE, AAUDIO_DIRECTION_OUTPUT);
}

TEST_F(AAudioTestSessionId, aaudio_session_id_alloc_lowlat_in) {
    checkSessionIdAllocate(AAUDIO_PERFORMANCE_MODE_LOW_LATENCY, AAUDIO_DIRECTION_INPUT);
}
TEST_F(AAudioTestSessionId, aaudio_session_id_alloc_lowlat_out) {
    checkSessionIdAllocate(AAUDIO_PERFORMANCE_MODE_LOW_LATENCY, AAUDIO_DIRECTION_OUTPUT);
}
