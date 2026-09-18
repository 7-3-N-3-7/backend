Feature: Media Content Management (MinIO) and Timestamped Transcripts (MongoDB)
  As a Therapist or Client on the INTEGRATE platform
  I want media files stored in MinIO object storage and detailed audio/video transcripts stored in MongoDB
  So that therapists can review sessions and clients can listen while transcript text highlights synchronously

  Background:
    Given MinIO object storage service is running on port 9000
    And MongoDB transcript collection "media_transcripts" is clean

  # =========================================================================
  # 1. MINIO S3 PRESIGNED URL GENERATION & MEDIA STORAGE
  # =========================================================================

  Scenario: Therapist requests presigned upload URL for session recording
    Given a valid therapist with UUID "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    When the therapist requests a presigned upload URL for file "session_2026_09_07.mp4" with MIME type "video/mp4"
    Then the response status should be 200 OK
    And the response body should contain a valid presigned S3 PUT URL pointing to MinIO bucket "integrate-media"
    And the generated object key should follow pattern "therapists/6ba7b810-9dad-11d1-80b4-00c04fd430c8/session_2026_09_07.mp4"

  Scenario: Prevent unauthorized user from requesting presigned upload URL for another therapist
    Given a client user with UUID "550e8400-e29b-41d4-a716-446655440000"
    When the client attempts to request an upload URL under therapist path "therapists/6ba7b810-9dad-11d1-80b4-00c04fd430c8/"
    Then the system should reject the request with HTTP 403 Forbidden

  # =========================================================================
  # 2. TIMESTAMPED TRANSCRIPT STORAGE & KAFKA EVENT STREAMING
  # =========================================================================

  Scenario: Save audio transcript JSON with word-level timestamps to MongoDB
    Given an uploaded media file in MinIO with object key "therapists/6ba7b810-9dad-11d1-80b4-00c04fd430c8/audio_01.mp3"
    When the transcription engine submits transcript payload:
      """
      {
        "mediaId": "media-uuid-998877",
        "objectKey": "therapists/6ba7b810-9dad-11d1-80b4-00c04fd430c8/audio_01.mp3",
        "language": "da",
        "words": [
          { "word": "Velkommen", "startMs": 0, "endMs": 450 },
          { "word": "til", "startMs": 460, "endMs": 600 },
          { "word": "sessionen", "startMs": 610, "endMs": 1200 }
        ]
      }
      """
    Then the transcript document should be successfully saved in MongoDB collection "media_transcripts"
    And a Kafka event message should be published to topic "transcript-events" containing mediaId "media-uuid-998877"

  Scenario: Retrieve timestamped transcript for synchronized media playback
    Given an existing transcript document in MongoDB for mediaId "media-uuid-998877"
    When a client or therapist sends a GET request to "/api/v1/transcripts/media-uuid-998877"
    Then the response status should be 200 OK
    And the response body should contain the array of 3 timestamped words
