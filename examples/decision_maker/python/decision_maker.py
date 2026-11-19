# Copyright 2026 The MediaPipe Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Answers questions about a text with the MediaPipe Decision Maker task.

  pip install mediapipe
  python decision_maker.py --text "We were charged twice on invoice #4821."

The Decision Maker answers yes/no questions, multiple-choice questions and
rating questions about a text, with a probability for every answer. This sample
triages a customer support ticket: is it an outage, which department owns it,
and how severe is it? The model (about 165 MB) is downloaded on first use.
"""

import argparse
import os
import urllib.request
from typing import Any, Dict, Optional, Sequence

from mediapipe.tasks import python
from mediapipe.tasks.python import decision

MODEL_URL = 'https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/main/embeddinggemma-2-text-270m.litertlm'

# The support ticket that is triaged by default.
INPUT_TEXT = ('Urgent: our production database pipeline crashes with a fatal '
              'segfault and customers cannot sign in!')

# A yes/no question.
URGENT = decision.BooleanQuestion(
    condition='The ticket reports an outage that stops customers from using '
    'the product.')

# A multiple-choice question: each answer has a description.
DEPARTMENT = decision.ChoiceQuestion(
    criteria={
        'bug': 'A software defect, crash or outage.',
        'billing': 'An invoice, charge, refund or payment.',
        'feature_request': 'A suggestion for a new feature or an improvement.',
    })

# A rating question: the levels of the scale, from lowest to highest.
SEVERITY = decision.ScoreQuestion(
    rubric=[
        'No impact: a question or a suggestion.',
        'Minor inconvenience.',
        'A problem that has a workaround.',
        'A major problem for some customers.',
        'Critical: the product is down for customers.',
    ])

DELEGATES = {
    'cpu': python.BaseOptions.Delegate.CPU,
    'gpu': python.BaseOptions.Delegate.GPU,
}


def download(url: str, path: str) -> None:
  """Downloads `url` to `path` unless the file is already there."""
  if os.path.exists(path):
    return
  print(f'Downloading {url}')
  urllib.request.urlretrieve(url, path + '.download')
  os.replace(path + '.download', path)


def run(model: str,
        input_text: str,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU
        ) -> Dict[str, Any]:
  """Triages a support ticket.

  Args:
    model: Path of the model.
    input_text: The text of the ticket.
    delegate: The hardware to run the model on.

  Returns:
    The answers, by the name of the question: a `BooleanResult` for "urgent",
    a `ChoiceResult` for "department" and a `ScoreResult` for "severity".
  """
  # Create the options for the Decision Maker
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = decision.DecisionMakerOptions(base_options=base_options)

  with decision.DecisionMaker.create_from_options(options) as decision_maker:
    # Ask all questions about the text at once
    decision_result = decision_maker.evaluate_schema(input_text, {
        'urgent': URGENT,
        'department': DEPARTMENT,
        'severity': SEVERITY,
    })

  # Print the answers
  urgent = decision_result['urgent']
  print(f'Urgent outage: {urgent.value} '
        f'(probability {urgent.probability_true:.2f})')

  department = decision_result['department']
  probability = department.probabilities[department.selected_key]
  print(f'Department: {department.selected_key} '
        f'(probability {probability:.2f})')

  # The expected score is a position on the scale, counted from 0.
  severity = decision_result['severity']
  print(f'Severity: {severity.expected_score:.2f} on a scale from 0 to '
        f'{len(SEVERITY.rubric) - 1}')

  return decision_result


def main(argv: Optional[Sequence[str]] = None) -> Dict[str, Any]:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the model. Downloaded if it is missing.',
      default='embeddinggemma-2-text-270m.litertlm')
  parser.add_argument(
      '--text', help='The support ticket to triage.', default=INPUT_TEXT)
  parser.add_argument(
      '--delegate',
      help='Hardware to run the model on.',
      choices=sorted(DELEGATES),
      default='cpu')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)

  return run(args.model, args.text, DELEGATES[args.delegate])


if __name__ == '__main__':
  main()
