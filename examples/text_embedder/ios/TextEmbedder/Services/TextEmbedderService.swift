// Copyright 2023 The MediaPipe Authors.
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

import MediaPipeTasksText

class TextEmbedderService {
  var textEmbedder: TextEmbedder?

  init(modelPath: String?) {
    guard let modelPath = modelPath else { return }
    self.textEmbedder = try? TextEmbedder(modelPath: modelPath)
  }

  func compare(text1: String?, text2: String?) -> Float? {
    guard let text1 = text1,
          let text2 = text2,
          let text1EmbedderResult = embed(text: text1)?.embeddings.first,
          let text2EmbedderResult = embed(text: text2)?.embeddings.first else {
      return nil
      
    }
     let cosineSimilarity = try? Float(
      truncating: TextEmbedder.cosineSimilarity(embedding1: text1EmbedderResult, embedding2: text2EmbedderResult))
    return cosineSimilarity ?? 0
  }

  func embed(text: String) -> EmbeddingResult? {
    guard let textEmbedder = textEmbedder else { return nil }
    return try? textEmbedder.embed(text: text).embeddingResult
  }
}
