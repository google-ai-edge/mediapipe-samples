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

#if canImport(DecisionMakerLib)
import DecisionMakerLib
#endif
import MediaPipeTasksDecision
import SwiftUI

/// Main landing view for the MediaPipe Decision Maker iOS sample app, radically simplified
/// to showcase instantaneous, on-device, multi-question decision making.
struct DecisionView: View {
  @State private var viewModel = DecisionViewModel()
  @State private var showSettings = false
  @State private var isVerdictSectionExpanded = false

  /// The 3 curated hero scenarios designed for instant verification by any audience.
  private let heroScenarios: [(id: String, title: String, icon: String, subtitle: String)] = [
    (
      id: "phishing",
      title: "Phishing Shield",
      icon: "shield.lefthalf.filled.badge.checkmark",
      subtitle: "Detects scams, phishing vectors & risk score"
    ),
    (
      id: "ticket_triage",
      title: "Support Triage",
      icon: "wrench.and.screwdriver.fill",
      subtitle: "Routes customer tickets, urgency & severity"
    ),
    (
      id: "llm_guardrails",
      title: "AI Guardrails",
      icon: "lock.shield.fill",
      subtitle: "Screens prompt injection, jailbreaks & safety"
    ),
  ]

  var body: some View {
    NavigationStack {
      VStack(spacing: 0) {
        performanceHudCard
          .padding(.horizontal, 16)
          .padding(.vertical, 5)
          .background(Color(uiColor: .systemBackground))

        Divider()

        ScrollViewReader { proxy in
          ScrollView {
            VStack(alignment: .leading, spacing: 18) {
              heroScenarioPicker
                .id("top")

              quickTestQueriesSection

              customInputSection

              verdictSection
            }
            .padding()
          }
          .scrollDismissesKeyboard(.interactively)
          .onAppear {
            proxy.scrollTo("top", anchor: .top)
          }
          .onChange(of: viewModel.selectedPresetId) { _, _ in
            isVerdictSectionExpanded = false
            proxy.scrollTo("top", anchor: .top)
          }
          .onChange(of: viewModel.inputText) { _, _ in
            isVerdictSectionExpanded = false
          }
        }
      }
      .navigationTitle("Decision Maker")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .topBarTrailing) {
          Button {
            showSettings = true
          } label: {
            Image(systemName: "gearshape")
              .font(.body.weight(.medium))
          }
        }
      }
      .sheet(isPresented: $showSettings) {
        DecisionSettingsView(viewModel: viewModel)
      }
    }
  }

  // MARK: - Performance HUD

  private var performanceHudCard: some View {
    VStack(spacing: 2) {
      HStack(spacing: 6) {
        if let latency = viewModel.evalLatencyMs {
          HStack(spacing: 3) {
            Image(systemName: "bolt.fill")
              .font(.caption2.bold())
            Text(String(format: "%.1f ms", latency))
              .font(.system(.caption2, design: .monospaced).bold())
          }
          .foregroundStyle(.green)

          Text("•")
            .font(.caption2)
            .foregroundStyle(.secondary.opacity(0.6))
        }

        HStack(spacing: 3) {
          Image(systemName: "cpu")
            .font(.caption2)
          Text(viewModel.selectedDelegate == .gpu ? "Metal (GPU)" : "XNNPACK (CPU)")
            .font(.caption2.weight(.medium))
        }
        .foregroundStyle(.secondary)

        Spacer()

        HStack(spacing: 3) {
          Image(systemName: "airplane")
            .font(.caption2)
          Text("100% Offline")
            .font(.caption2.weight(.medium))
        }
        .foregroundStyle(.secondary)
      }

      if let error = viewModel.errorMessage {
        Text(error)
          .font(.caption2)
          .foregroundStyle(.red)
          .frame(maxWidth: .infinity, alignment: .leading)
      }
    }
  }

  // MARK: - Hero Scenario Picker

  private var heroScenarioPicker: some View {
    VStack(alignment: .leading, spacing: 8) {
      Text("SELECT SCENARIO")
        .font(.caption.weight(.bold))
        .foregroundStyle(.secondary)

      HStack(spacing: 8) {
        ForEach(heroScenarios, id: \.id) { scenario in
          let isSelected = viewModel.selectedPresetId == scenario.id
          Button {
            viewModel.selectPreset(scenario.id)
          } label: {
            VStack(spacing: 4) {
              Image(systemName: scenario.icon)
                .font(.system(size: 18))
              Text(scenario.title)
                .font(.caption.bold())
                .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .padding(.horizontal, 6)
            .background(isSelected ? Color.secondary.opacity(0.22) : Color.secondary.opacity(0.06))
            .overlay(
              RoundedRectangle(cornerRadius: 10)
                .stroke(isSelected ? Color.primary.opacity(0.3) : Color.clear, lineWidth: 1.5)
            )
            .foregroundStyle(isSelected ? Color.primary : Color.secondary)
            .clipShape(RoundedRectangle(cornerRadius: 10))
          }
          .buttonStyle(.plain)
          .disabled(viewModel.isEvaluating)
        }
      }

      if let currentScenario = heroScenarios.first(where: { $0.id == viewModel.selectedPresetId }) {
        Text(currentScenario.subtitle)
          .font(.caption2)
          .foregroundStyle(.secondary)
          .padding(.horizontal, 2)
      }
    }
  }

  // MARK: - Quick Test Candidate Queries

  private var quickTestQueriesSection: some View {
    VStack(alignment: .leading, spacing: 8) {
      Text("SAMPLE PROMPTS (TAP TO PREFILL)")
        .font(.caption.weight(.bold))
        .foregroundStyle(.secondary)

      VStack(spacing: 6) {
        ForEach(Array(viewModel.candidateQueries.enumerated()), id: \.offset) { _, query in
          let isCurrent = viewModel.inputText == query
          Button {
            viewModel.applyCandidateQuery(query)
          } label: {
            HStack(alignment: .top, spacing: 10) {
              Image(systemName: queryIcon(for: query))
                .font(.caption)
                .foregroundStyle(isCurrent ? Color.primary : Color.secondary)
                .frame(width: 16)
                .padding(.top, 2)

              Text(query)
                .font(.caption)
                .foregroundStyle(isCurrent ? Color.primary : Color.secondary)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isCurrent ? Color.secondary.opacity(0.12) : Color.secondary.opacity(0.04))
            .overlay(
              RoundedRectangle(cornerRadius: 8)
                .stroke(isCurrent ? Color.primary.opacity(0.25) : Color.clear, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 8))
          }
          .buttonStyle(.plain)
          .disabled(viewModel.isEvaluating)
        }
      }
    }
  }

  // MARK: - Custom Input Field

  private var customInputSection: some View {
    VStack(alignment: .leading, spacing: 6) {
      Text("INPUT TEXT")
        .font(.caption.weight(.bold))
        .foregroundStyle(.secondary)

      HStack(alignment: .bottom, spacing: 8) {
        TextField("Input text to evaluate...", text: $viewModel.inputText, axis: .vertical)
          .font(.subheadline)
          .lineLimit(2...4)
          .padding(8)
          .background(Color.secondary.opacity(0.06))
          .clipShape(RoundedRectangle(cornerRadius: 8))
          .overlay(
            RoundedRectangle(cornerRadius: 8)
              .stroke(Color.secondary.opacity(0.25), lineWidth: 1)
          )
          .onSubmit {
            Task {
              await viewModel.evaluateScenario()
            }
          }

        VStack(spacing: 4) {
          if viewModel.showSlowInferenceIndicator {
            ProgressView()
              .controlSize(.small)
              .transition(.opacity.combined(with: .scale(scale: 0.85)))
          }

          Button {
            Task {
              await viewModel.evaluateScenario()
            }
          } label: {
            VStack(spacing: 2) {
              Image(systemName: "arrow.forward.circle.fill")
                .font(.title2)
              Text("Decide")
                .font(.caption2.bold())
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(viewModel.isEvaluating ? Color.blue.opacity(0.4) : Color.blue)
            .foregroundStyle(viewModel.isEvaluating ? Color.white.opacity(0.7) : Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 8))
          }
          .buttonStyle(.plain)
          .disabled(viewModel.isEvaluating || !viewModel.isModelLoaded)
        }
        .animation(.easeInOut(duration: 0.2), value: viewModel.showSlowInferenceIndicator)
        .animation(.default, value: viewModel.isEvaluating)
      }
    }
  }

  // MARK: - High-Impact Verdict Section

  private var primaryVerdictSummary: (color: Color, flag: String, percentage: String, icon: String)
  {
    if let boolRes = viewModel.questionResults.first(where: { $0.type == .boolean }) {
      let isTrue =
        boolRes.headline.uppercased().contains("YES")
        || boolRes.headline.uppercased().contains("TRUE")
      let probTrue = boolRes.bars.first { $0.label.contains("True") }?.probability ?? 0.5
      let winningProb = isTrue ? probTrue : (1.0 - probTrue)
      let color: Color = isTrue ? .red : .green
      let flag = isTrue ? "FLAGGED: POSITIVE" : "CLEARED: NEGATIVE"
      let icon = isTrue ? "exclamationmark.triangle.fill" : "checkmark.shield.fill"
      return (color, flag, String(format: "%.1f%%", winningProb * 100.0), icon)
    } else if let firstRes = viewModel.questionResults.first {
      let topBar = firstRes.bars.max(by: { $0.probability < $1.probability })
      let prob = topBar?.probability ?? firstRes.confidence
      let formattedHeadline = firstRes.headline.replacingOccurrences(of: "_", with: " ").capitalized
      return (.blue, formattedHeadline, String(format: "%.1f%%", prob * 100.0), "tag.fill")
    }
    return (.secondary, "UNKNOWN", "0.0%", "questionmark.circle")
  }

  @ViewBuilder
  private var verdictSection: some View {
    if !viewModel.questionResults.isEmpty {
      let summary = primaryVerdictSummary

      VStack(alignment: .leading, spacing: 8) {
        HStack {
          Text("INSTANT VERDICTS")
            .font(.caption.weight(.bold))
            .foregroundStyle(.secondary)
          Spacer()
          Text(isVerdictSectionExpanded ? "Hide Details" : "Single Forward Pass")
            .font(.caption2)
            .foregroundStyle(.secondary)
        }

        // Collapsible Card: when collapsed, only apply color and display flag and percentage
        Button {
          withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
            isVerdictSectionExpanded.toggle()
          }
        } label: {
          HStack(spacing: 12) {
            Image(systemName: summary.icon)
              .font(.title3.bold())
              .foregroundStyle(summary.color)

            Text(summary.flag)
              .font(.headline.bold())
              .foregroundStyle(summary.color)

            Spacer()

            Text(summary.percentage)
              .font(.system(.title3, design: .monospaced).bold())
              .foregroundStyle(summary.color)

            Image(systemName: isVerdictSectionExpanded ? "chevron.up" : "chevron.down")
              .font(.footnote.bold())
              .foregroundStyle(summary.color.opacity(0.8))
          }
          .padding(.horizontal, 14)
          .padding(.vertical, 12)
          .background(summary.color.opacity(0.12))
          .overlay(
            RoundedRectangle(cornerRadius: 12)
              .stroke(summary.color.opacity(0.35), lineWidth: 1.5)
          )
          .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)

        if isVerdictSectionExpanded {
          VStack(alignment: .leading, spacing: 10) {
            ForEach(viewModel.questionResults) { result in
              switch result.type {
              case .boolean:
                booleanVerdictCard(result)
              case .choice:
                choiceVerdictCard(result)
              case .score:
                scoreVerdictCard(result)
              }
            }
          }
          .padding(.top, 4)
          .transition(.opacity.combined(with: .move(edge: .top)))
        }
      }
    }
  }

  private func booleanVerdictCard(_ result: PolymorphicQuestionResult) -> some View {
    let isTrue =
      result.headline.uppercased().contains("YES") || result.headline.uppercased().contains("TRUE")
    let cardColor = isTrue ? Color.red : Color.green

    return VStack(alignment: .leading, spacing: 6) {
      HStack {
        Image(systemName: isTrue ? "exclamationmark.triangle.fill" : "checkmark.shield.fill")
          .font(.subheadline)
          .foregroundStyle(cardColor)

        Text(result.prompt)
          .font(.caption.weight(.medium))
          .foregroundStyle(.secondary)

        Spacer()

        Text(result.subheadline)
          .font(.system(.caption2, design: .monospaced).bold())
          .foregroundStyle(cardColor)
      }

      HStack {
        Text(isTrue ? "FLAGGED: POSITIVE" : "CLEARED: NEGATIVE")
          .font(.headline.bold())
          .foregroundStyle(cardColor)

        Spacer()

        let probTrue = result.bars.first { $0.label.contains("True") }?.probability ?? 0.5
        let winningProb = isTrue ? probTrue : (1.0 - probTrue)
        Text(String(format: "P(%@) = %.1f%%", isTrue ? "True" : "False", winningProb * 100.0))
          .font(.caption.bold())
          .padding(.horizontal, 8)
          .padding(.vertical, 3)
          .background(cardColor.opacity(0.15))
          .foregroundStyle(cardColor)
          .clipShape(Capsule())
      }
    }
    .padding(12)
    .background(cardColor.opacity(0.08))
    .overlay(
      RoundedRectangle(cornerRadius: 10)
        .stroke(cardColor.opacity(0.3), lineWidth: 1)
    )
    .clipShape(RoundedRectangle(cornerRadius: 10))
  }

  private func choiceVerdictCard(_ result: PolymorphicQuestionResult) -> some View {
    VStack(alignment: .leading, spacing: 8) {
      HStack {
        Image(systemName: "tag.fill")
          .font(.caption)
          .foregroundStyle(.blue)

        Text(result.prompt)
          .font(.caption.weight(.medium))
          .foregroundStyle(.secondary)

        Spacer()

        Text(String(format: "Confidence: %.0f%%", result.confidence * 100.0))
          .font(.system(.caption2, design: .monospaced).bold())
          .foregroundStyle(.blue)
      }

      Text(result.headline.replacingOccurrences(of: "_", with: " ").capitalized)
        .font(.title3.bold())
        .foregroundStyle(.primary)

      VStack(spacing: 4) {
        ForEach(Array(result.bars.prefix(3))) { bar in
          HStack(spacing: 6) {
            Text(bar.label.replacingOccurrences(of: "_", with: " "))
              .font(.caption2)
              .foregroundStyle(.secondary)
              .frame(width: 140, alignment: .leading)

            ProgressView(value: Double(min(max(bar.probability, 0.0), 1.0)))
              .tint(.blue)

            Text(String(format: "%.1f%%", bar.probability * 100.0))
              .font(.system(.caption2, design: .monospaced))
              .frame(width: 45, alignment: .trailing)
          }
        }
      }
    }
    .padding(12)
    .background(Color.secondary.opacity(0.06))
    .clipShape(RoundedRectangle(cornerRadius: 10))
  }

  private func scoreVerdictCard(_ result: PolymorphicQuestionResult) -> some View {
    VStack(alignment: .leading, spacing: 8) {
      HStack {
        Image(systemName: "gauge.with.dots.needle.bottom.50percent")
          .font(.caption)
          .foregroundStyle(.orange)

        Text(result.prompt)
          .font(.caption.weight(.medium))
          .foregroundStyle(.secondary)

        Spacer()

        Text(result.subheadline)
          .font(.system(.caption2, design: .monospaced))
          .foregroundStyle(.secondary)
      }

      HStack(alignment: .firstTextBaseline) {
        Text(result.headline)
          .font(.headline.bold())
          .foregroundStyle(.primary)

        Spacer()
      }

      VStack(spacing: 4) {
        ForEach(Array(result.bars.prefix(5))) { bar in
          HStack(spacing: 6) {
            Text(bar.label)
              .font(.caption2)
              .foregroundStyle(.secondary)
              .frame(width: 90, alignment: .leading)

            ProgressView(value: Double(min(max(bar.probability, 0.0), 1.0)))
              .tint(.orange)

            Text(String(format: "%.1f%%", bar.probability * 100.0))
              .font(.system(.caption2, design: .monospaced))
              .frame(width: 45, alignment: .trailing)
          }
        }
      }
    }
    .padding(12)
    .background(Color.orange.opacity(0.06))
    .clipShape(RoundedRectangle(cornerRadius: 10))
  }

  private func queryIcon(for query: String) -> String {
    let lower = query.lowercased()
    if lower.contains("password") || lower.contains("auth") || lower.contains("login") {
      return "key.fill"
    } else if lower.contains("gift card") || lower.contains("charge") || lower.contains("invoice")
      || lower.contains("paid")
    {
      return "creditcard.fill"
    } else if lower.contains("delivery") || lower.contains("package") || lower.contains("usps") {
      return "shippingbox.fill"
    } else if lower.contains("database") || lower.contains("segfault") || lower.contains("crash")
      || lower.contains("error")
    {
      return "exclamationmark.octagon.fill"
    } else if lower.contains("ignore") || lower.contains("malware") || lower.contains("keylogger") {
      return "nosign"
    } else if lower.contains("lunch") || lower.contains("cafe") || lower.contains("meet") {
      return "cup.and.saucer.fill"
    } else if lower.contains("python") || lower.contains("sql") || lower.contains("doc")
      || lower.contains("slides")
    {
      return "checkmark.circle.fill"
    }
    return "text.bubble"
  }
}

// MARK: - Dedicated Settings & Plumbing Sheet

struct DecisionSettingsView: View {
  @Environment(\.dismiss) private var dismiss
  @Bindable var viewModel: DecisionViewModel

  var body: some View {
    NavigationStack {
      Form {
        Section("Model & Acceleration") {
          Picker(
            "Model Architecture",
            selection: Binding(
              get: { viewModel.selectedModel },
              set: { viewModel.selectAndLoadModel($0) }
            )
          ) {
            ForEach(SupportedDecisionModel.allCases) { model in
              Text(model.displayName).tag(model)
            }
          }
          .disabled(viewModel.isEvaluating)

          Picker(
            "Hardware Delegate",
            selection: Binding(
              get: { viewModel.selectedDelegate },
              set: { viewModel.selectAndLoadDelegate($0) }
            )
          ) {
            Text("GPU (Metal)").tag(DecisionMakerDelegate.gpu)
            Text("CPU (XNNPACK)").tag(DecisionMakerDelegate.cpu)
          }
          .disabled(viewModel.isEvaluating)

          Text(viewModel.modelStatusLabel)
            .font(.caption)
            .foregroundStyle(.secondary)

          HStack {
            Button(viewModel.isModelLoaded ? "Reload" : "Load Model") {
              viewModel.loadModel()
            }
            .disabled(viewModel.isEvaluating)
            Spacer()
            Button("Prewarm Schema") {
              viewModel.prewarmSchema()
            }
            .disabled(!viewModel.isModelLoaded || viewModel.isEvaluating)
            Spacer()
            Button("Unload", role: .destructive) {
              viewModel.unloadModel()
            }
            .disabled(!viewModel.isModelLoaded || viewModel.isEvaluating)
          }
        }

        Section("Benchmark Timings") {
          HStack {
            Text("Model Load")
            Spacer()
            Text(viewModel.loadLatencyMs.map { String(format: "%.1f ms", $0) } ?? "—")
              .font(.system(.body, design: .monospaced))
          }
          HStack {
            Text("Schema Prewarm")
            Spacer()
            Text(viewModel.prewarmLatencyMs.map { String(format: "%.1f ms", $0) } ?? "—")
              .font(.system(.body, design: .monospaced))
          }
          HStack {
            Text("Query Evaluation")
            Spacer()
            Text(viewModel.evalLatencyMs.map { String(format: "%.1f ms", $0) } ?? "—")
              .font(.system(.body, design: .monospaced).bold())
              .foregroundStyle(.blue)
          }
        }

        Section("All Presets (Including High-Cardinality)") {
          Picker(
            "Active Preset",
            selection: Binding(
              get: { viewModel.selectedPresetId },
              set: { viewModel.selectPreset($0) }
            )
          ) {
            ForEach(DecisionViewModel.defaultPresets) { preset in
              Text(preset.title).tag(preset.id)
            }
          }
          .disabled(viewModel.isEvaluating)
          Text(viewModel.scenarioSubtitle)
            .font(.caption2)
            .foregroundStyle(.secondary)
        }

        Section("Advanced: Raw JSON Schema") {
          DisclosureGroup("Custom Prompts & Options (\(viewModel.questions.count) Questions)") {
            ForEach(Array(viewModel.questions.indices), id: \.self) { qIdx in
              VStack(alignment: .leading, spacing: 6) {
                Text(viewModel.questions[qIdx].id)
                  .font(.system(.caption, design: .monospaced).bold())
                TextField("Prompt", text: $viewModel.questions[qIdx].prompt, axis: .vertical)
                  .textFieldStyle(.roundedBorder)
              }
              .padding(.vertical, 4)
            }
          }
          .disabled(viewModel.isEvaluating)

          if let raw = viewModel.rawJsonResult {
            DisclosureGroup("Raw Response JSON") {
              Text(raw)
                .font(.system(.caption2, design: .monospaced))
                .textSelection(.enabled)
            }
          }
        }
      }
      .navigationTitle("Engine Settings")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .topBarTrailing) {
          Button("Done") {
            dismiss()
          }
          .fontWeight(.semibold)
        }
      }
    }
  }
}
