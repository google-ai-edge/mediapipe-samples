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

import AVFoundation
import Foundation
import QuartzCore
import MediaPipeTasksAudio

protocol AudioClassifierHelperDelegate: AnyObject {
  func audioClassifierHelper(
    _ helper: AudioClassifierHelper,
    didFinishClassification categories: [ResultCategory],
    inferenceTime: Double
  )
  func audioClassifierHelper(
    _ helper: AudioClassifierHelper,
    didFailWithError error: Error
  )
}

class AudioClassifierHelper {

  weak var delegate: AudioClassifierHelperDelegate?

  private var audioClassifier: AudioClassifier?
  private var audioRecord: AudioRecord?
  private var timer: Timer?

  private let modelPath: String
  var scoreThreshold: Float
  var maxResults: Int
  private let sampleRate: Double = 16000.0
  private let channelCount: Int = 1
  private var lastInferenceStartTime: Double = 0

  init?(modelPath: String, scoreThreshold: Float = 0.3, maxResults: Int = 3) {
    self.modelPath = modelPath
    self.scoreThreshold = scoreThreshold
    self.maxResults = maxResults
  }

  func startLiveClassification() {
    let session = AVAudioSession.sharedInstance()
    session.requestRecordPermission { [weak self] granted in
      guard let self = self else { return }
      guard granted else {
        self.delegate?.audioClassifierHelper(
          self,
          didFailWithError: NSError(
            domain: "AudioClassifierHelper",
            code: -1,
            userInfo: [NSLocalizedDescriptionKey: "Microphone permission was denied"]
          )
        )
        return
      }

      DispatchQueue.main.async {
        do {
          try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
          try session.setActive(true)

          var options = AudioClassifierOptions()
          options.baseOptions.modelAssetPath = self.modelPath
          options.runningMode = .audioStream
          options.scoreThreshold = self.scoreThreshold
          options.maxResults = self.maxResults
          options.audioStreamResultHandler = { [weak self] result in
            self?.handleClassificationResult(result)
          }

          self.audioClassifier = try AudioClassifier(options: options)

          let format = AudioDataFormat(channelCount: self.channelCount, sampleRate: self.sampleRate)
          let bufferLength = Int(self.sampleRate * 2)
          self.audioRecord = try AudioRecord(audioDataFormat: format, bufferLength: bufferLength)

          try self.audioRecord?.startRecording()

          // Classify audio every 500ms
          self.timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            self?.classifyAsync()
          }
        } catch {
          self.delegate?.audioClassifierHelper(self, didFailWithError: error)
        }
      }
    }
  }

  private func classifyAsync() {
    guard let audioRecord = audioRecord, let audioClassifier = audioClassifier else { return }
    do {
      let audioFormat = AudioDataFormat(channelCount: channelCount, sampleRate: sampleRate)
      let sampleCount = Int(sampleRate * 0.975)
      let audioData = AudioData(format: audioFormat, sampleCount: sampleCount)
      try audioData.load(audioRecord: audioRecord)

      lastInferenceStartTime = CACurrentMediaTime()
      let timestamp = Int(CACurrentMediaTime() * 1000)
      try audioClassifier.classifyAsync(audioBlock: audioData, timestampInMilliseconds: timestamp)
    } catch {
      delegate?.audioClassifierHelper(self, didFailWithError: error)
    }
  }

  func stopLiveClassification() {
    timer?.invalidate()
    timer = nil

    do {
      try audioRecord?.stop()
      audioRecord = nil
      try audioClassifier?.close()
      audioClassifier = nil
      try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    } catch {
      print("Error stopping audio classification: \(error)")
    }
  }

  private func handleClassificationResult(_ result: Result<AudioClassifierResult, any Error>) {
    switch result {
    case .failure(let error):
      DispatchQueue.main.async {
        self.delegate?.audioClassifierHelper(self, didFailWithError: error)
      }
    case .success(let classificationResult):
      let inferenceTime = (CACurrentMediaTime() - lastInferenceStartTime) * 1000.0
      var categories: [ResultCategory] = []
      if let head = classificationResult.classificationResults.first?.classifications.first {
        categories = head.categories
      }

      DispatchQueue.main.async {
        self.delegate?.audioClassifierHelper(
          self,
          didFinishClassification: categories,
          inferenceTime: max(1.0, inferenceTime)
        )
      }
    }
  }
}
