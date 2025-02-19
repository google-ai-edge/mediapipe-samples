// Copyright 2024 The Mediapipe Authors.
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

import SwiftUI

struct ModelSelectionScreen: View {
  var body: some View {
    NavigationStack {
      List(Model.allCases, id: \.self) { model in
        NavigationLink(model.name, value: model)
      }
      .navigationTitle("Models")
      .navigationBarTitleDisplayMode(.inline)
      .navigationDestination(for: Model.self) { model in
        ConversationScreen(viewModel: ConversationViewModel(modelCategory: model))
      }
    }
  }
}
