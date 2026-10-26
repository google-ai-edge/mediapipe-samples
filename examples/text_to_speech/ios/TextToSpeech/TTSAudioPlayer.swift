// Copyright 2026 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import Foundation

#if canImport(MediaPipeTasksAudioTTS)
  import MediaPipeTasksAudioTTS
#elseif canImport(MediaPipeTTS)
  import MediaPipeTTS
#else
  import third_party_mediapipe_tasks_ios_audio_text_to_speech_TextToSpeech
#endif

#if canImport(AVFoundation)
  import AVFoundation
#endif

/// Sample-layer PCM audio playback & WAV export utility.
public final class TTSAudioPlayer: @unchecked Sendable {
  private let lock = NSLock()
  private var accumulatedSamples: [Float] = []
  private var sampleRateHz: Int = 24000
  private var enqueuedChunkCount: Int = 0
  public let enableSpeakerOutput: Bool

  #if canImport(AVFoundation)
    private var audioEngine: AVAudioEngine?
    private var playerNode: AVAudioPlayerNode?
  #endif

  public init(enableSpeakerOutput: Bool = false) {
    self.enableSpeakerOutput = enableSpeakerOutput
    #if canImport(AVFoundation)
      if enableSpeakerOutput {
        #if os(iOS)
          do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [])
            try session.setActive(true)
            print("AVAudioSession configured for playback successfully")
          } catch {
            print("Failed to configure AVAudioSession: \(error)")
          }
        #endif
        let engine = AVAudioEngine()
        let node = AVAudioPlayerNode()
        engine.attach(node)
        let format = AVAudioFormat(
          commonFormat: .pcmFormatFloat32,
          sampleRate: 24000,
          channels: 1,
          interleaved: false
        )!
        engine.connect(node, to: engine.mainMixerNode, format: format)
        do {
          try engine.start()
          print("AVAudioEngine started successfully")
        } catch {
          print("Failed to start AVAudioEngine: \(error)")
        }
        node.play()
        self.audioEngine = engine
        self.playerNode = node
      }
    #endif
  }

  /// Enqueues a synthesized `AudioResult` for gapless playback and WAV recording.
  public func enqueue(_ result: AudioResult, completion: (@Sendable () -> Void)? = nil) {
    lock.lock()
    if result.sampleRateHz > 0 {
      sampleRateHz = result.sampleRateHz
    }
    let effectiveRate = sampleRateHz > 0 ? sampleRateHz : 24000
    accumulatedSamples.append(contentsOf: result.samples)
    enqueuedChunkCount += 1
    lock.unlock()

    #if canImport(AVFoundation)
      if enableSpeakerOutput, let node = playerNode, !result.samples.isEmpty {
        guard
          let format = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: Double(effectiveRate),
            channels: 1,
            interleaved: false
          ),
          let pcmBuffer = AVAudioPCMBuffer(
            pcmFormat: format,
            frameCapacity: AVAudioFrameCount(result.samples.count)
          )
        else {
          completion?()
          return
        }
        pcmBuffer.frameLength = AVAudioFrameCount(result.samples.count)
        if let channelData = pcmBuffer.floatChannelData?[0] {
          for i in 0..<result.samples.count {
            let s = result.samples[i]
            channelData[i] = (s.isNaN || s.isInfinite) ? 0.0 : max(-1.0, min(1.0, s))
          }
          node.scheduleBuffer(pcmBuffer) {
            completion?()
          }
          if !node.isPlaying {
            node.play()
            print("AVAudioPlayerNode started playing")
          }
        } else {
          completion?()
        }
      } else {
        completion?()
      }
    #else
      completion?()
    #endif
  }

  /// Enqueues a streamed `AudioChunk` for gapless playback and WAV recording.
  public func enqueue(_ chunk: AudioChunk, completion: (@Sendable () -> Void)? = nil) {
    enqueue(
      AudioResult(samples: chunk.samples, sampleRateHz: chunk.sampleRateHz),
      completion: completion
    )
  }

  /// Immediately halts speaker playback and clears buffered audio (invoked on user barge-in).
  public func stop() {
    #if canImport(AVFoundation)
      playerNode?.stop()
    #endif
    lock.lock()
    accumulatedSamples.removeAll()
    enqueuedChunkCount = 0
    lock.unlock()
  }

  /// Returns total accumulated sample count across enqueued audio results.
  public var totalSamples: Int {
    lock.lock()
    defer { lock.unlock() }
    return accumulatedSamples.count
  }

  /// Returns number of audio buffers enqueued since last `stop()`.
  public var chunkCount: Int {
    lock.lock()
    defer { lock.unlock() }
    return enqueuedChunkCount
  }

  /// Writes the accumulated mono PCM samples to a 16-bit RIFF `.wav` file.
  public func writeWavFile(to path: String) throws {
    lock.lock()
    let samples = accumulatedSamples
    let rate = UInt32(sampleRateHz > 0 ? sampleRateHz : 24000)
    lock.unlock()

    let numSamples = UInt32(samples.count)
    let bytesPerSample: UInt16 = 2
    let numChannels: UInt16 = 1
    let dataSize = numSamples * UInt32(bytesPerSample)
    let riffSize = 36 + dataSize
    let byteRate = rate * UInt32(numChannels) * UInt32(bytesPerSample)
    let blockAlign = numChannels * bytesPerSample
    let bitsPerSample: UInt16 = 16

    var data = Data(capacity: Int(44 + dataSize))
    data.append(contentsOf: "RIFF".utf8)
    withUnsafeBytes(of: riffSize.littleEndian) { data.append(contentsOf: $0) }
    data.append(contentsOf: "WAVE".utf8)
    data.append(contentsOf: "fmt ".utf8)
    withUnsafeBytes(of: UInt32(16).littleEndian) { data.append(contentsOf: $0) }
    withUnsafeBytes(of: UInt16(1).littleEndian) { data.append(contentsOf: $0) }
    withUnsafeBytes(of: numChannels.littleEndian) { data.append(contentsOf: $0) }
    withUnsafeBytes(of: rate.littleEndian) { data.append(contentsOf: $0) }
    withUnsafeBytes(of: byteRate.littleEndian) { data.append(contentsOf: $0) }
    withUnsafeBytes(of: blockAlign.littleEndian) { data.append(contentsOf: $0) }
    withUnsafeBytes(of: bitsPerSample.littleEndian) { data.append(contentsOf: $0) }
    data.append(contentsOf: "data".utf8)
    withUnsafeBytes(of: dataSize.littleEndian) { data.append(contentsOf: $0) }

    for s in samples {
      let clamped = max(-1.0, min(1.0, s))
      let pcm16 = Int16(clamped * 32767.0).littleEndian
      withUnsafeBytes(of: pcm16) { data.append(contentsOf: $0) }
    }

    try data.write(to: URL(fileURLWithPath: path))
  }
}
