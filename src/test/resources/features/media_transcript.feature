Feature: Media Upload and Transcript Synchronization
  As a Therapist user on the platform
  I want to upload session audio/video recordings to MinIO and store transcript JSONs in MongoDB
  So that media playback can be synchronized with word-level audio timestamps for clients

  Background:
    Given MinIO S3 object storage is running
    And MongoDB transcript storage is initialized

  Scenario: Request presigned S3 URL for therapist media upload
    Given an authenticated therapist with UUID "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    When the therapist requests a upload URL for file "session_audio.mp3" of type "audio/mp3"
    Then the backend should generate a presigned MinIO S3 upload URL
    And respond with HTTP status 200 OK

  Scenario: Save session audio transcript with timestamps to MongoDB
    Given a completed audio upload with media ID "media-uuid-12345"
    When a transcript JSON payload with 150 word timestamps is submitted to "/api/v1/transcripts"
    Then the transcript document should be persisted in MongoDB
    And a Kafka event "transcript-processed" should be published for real-time client sync
