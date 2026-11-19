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
"""Writes the validation report: status.json, report.md and index.html."""

import html
import json
import os

# What each status means. PASS is the only one that says "this works".
STATUSES = {
    'PASS': ('\u2705', 'the sample ran and every output check passed'),
    'FAIL': ('\u274c', 'the sample failed, or its output is wrong'),
    'GPU_UNCONFIRMED': ('\u26a0\ufe0f', 'the output is correct, but the log has no sign that the GPU was used'),
    'UNSUPPORTED': ('\u23ed\ufe0f', 'MediaPipe rejects this combination with the documented error'),
    'UNVERIFIED': ('\u2754', 'the sample ran, but no output check is defined'),
}
ORDER = ['FAIL', 'GPU_UNCONFIRMED', 'UNVERIFIED', 'UNSUPPORTED', 'PASS']


def summarize_counts(results):
  counts = {status: 0 for status in STATUSES}
  for result in results:
    counts[result['status']] = counts.get(result['status'], 0) + 1
  counts['total'] = len(results)
  return counts


def status_icon(status):
  return f'{STATUSES.get(status, ("", ""))[0]} {status}'.strip()


def _md(text):
  return str(text).replace('|', '\\|').replace('\n', ' ')


def _environment_lines(state):
  env = state['environment']
  lines = [
      f"MediaPipe under test: {env.get('mediapipeVersion') or 'not installed'} (requested: {env['mediapipeSpec']})",
      f"Python: {env.get('pythonVersion')}",
      f"System: {env.get('system')}",
      f"Processor: {env.get('processor')}",
      f"GPU: {env.get('gpu')}",
      f"Samples: commit {env.get('gitCommit')}{' with local changes' if env.get('gitDirty') else ''}",
      f"Started: {state['startedAt']}, finished: {state['finishedAt']}",
  ]
  if env.get('pythonPinned') and not env.get('pythonMatchesPins'):
    lines.append(f"Note: the pinned dependencies were resolved for Python {env['pythonPinned']}; "
                 'this run used another version.')
  if not env.get('constraints'):
    lines.append('Note: the dependencies were NOT pinned (--no-constraints).')
  return lines


def format_markdown(state):
  results = state['results']
  counts = summarize_counts(results)
  lines = ['# MediaPipe Python release validation', '']
  lines += [f'- {line}' for line in _environment_lines(state)]
  lines += ['', '## Summary', '', f"**{state['verdict']}**", '']
  for status in ORDER:
    if counts.get(status):
      lines.append(f'- {status_icon(status)}: **{counts[status]}** ({STATUSES[status][1]})')
  lines.append(f"- Total: **{counts['total']}**")
  if state.get('baseline'):
    b = state['baseline']
    lines.append(f"- Compared with {b['path']}: {b['same']} identical, {b['different']} different, "
                 f"{b['new']} not in the baseline")
  lines += ['', '## Cases', '',
            '| # | Task | Model | Mode | Delegate | Status | Output checks | Seconds | Details |',
            '|---|---|---|---|---|---|---|---|---|']
  for index, r in enumerate(results):
    lines.append(
        f"| {index + 1} | {_md(r['domain'] + '/' + r['task'])} | {_md(r['modelId'])} | {_md(r['mode'])} | "
        f"{r['delegate']} | {status_icon(r['status'])} | {_md(r['verification']['summary'])} | "
        f"{r['durationSec']:.1f} | {_md(r['details'][:500])} |")
  return '\n'.join(lines) + '\n'


_CSS = """
body { font: 14px/1.45 -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 24px auto; max-width: 1180px;
       padding: 0 16px; color: #1f2328; }
h1 { font-size: 22px; } h2 { font-size: 17px; margin-top: 28px; }
ul.env { padding-left: 18px; color: #444; }
.verdict { font-size: 16px; font-weight: 600; padding: 10px 14px; border-radius: 6px; display: inline-block; }
.verdict.ok { background: #dafbe1; } .verdict.bad { background: #ffebe9; }
.counts span { display: inline-block; margin: 8px 14px 0 0; }
details.case { border: 1px solid #d0d7de; border-radius: 6px; margin: 6px 0; }
details.case > summary { cursor: pointer; padding: 7px 10px; display: flex; gap: 10px; align-items: baseline; }
details.case > summary:hover { background: #f6f8fa; }
.badge { font-weight: 600; font-size: 12px; padding: 1px 8px; border-radius: 10px; white-space: nowrap; }
.PASS { background: #dafbe1; color: #116329; } .FAIL { background: #ffebe9; color: #a40e26; }
.GPU_UNCONFIRMED, .UNVERIFIED { background: #fff8c5; color: #7d4e00; } .UNSUPPORTED { background: #eaeef2; color: #57606a; }
.title { font-weight: 600; min-width: 330px; } .why { color: #57606a; overflow: hidden; text-overflow: ellipsis;
         white-space: nowrap; flex: 1; } .time { color: #8c959f; font-variant-numeric: tabular-nums; }
.body { padding: 4px 14px 14px; border-top: 1px solid #d0d7de; }
table { border-collapse: collapse; width: 100%; margin: 6px 0; } th, td { text-align: left; padding: 3px 8px;
        border-bottom: 1px solid #eaeef2; vertical-align: top; } th { color: #57606a; font-weight: 600; }
tr.bad td { background: #ffebe9; } td.mark { width: 22px; }
.media { display: flex; flex-wrap: wrap; gap: 12px; } figure { margin: 0; } figure img { max-height: 240px;
         max-width: 380px; border: 1px solid #d0d7de; border-radius: 4px; } figcaption { color: #57606a; font-size: 12px;
         max-width: 380px; }
pre { background: #f6f8fa; padding: 8px 10px; border-radius: 6px; overflow-x: auto; white-space: pre-wrap; font-size: 12px; }
.text-input { background: #f6f8fa; border-radius: 6px; padding: 6px 10px; margin: 4px 0; }
.text-input small { color: #57606a; display: block; }
"""


def _figure(path, caption):
  return (f'<figure><a href="{html.escape(path)}"><img loading="lazy" src="{html.escape(path)}"></a>'
          f'<figcaption>{html.escape(caption)}</figcaption></figure>')


def _case_html(r):
  e = html.escape
  parts = [f'<p>{e(r["description"])}</p>']
  if r.get('note'):
    parts.append(f'<p><b>Note:</b> {e(r["note"])}</p>')
  if r.get('error'):
    parts.append(f'<h4>Error</h4><pre>{e(r["error"])}</pre>')
  if r.get('traceback'):
    parts.append(f'<details><summary>Traceback</summary><pre>{e(r["traceback"])}</pre></details>')

  media, texts = [], []
  for item in r.get('inputs', []):
    if item['kind'] == 'image':
      media.append(_figure(item['file'], 'Input: ' + item['label']))
    elif item['kind'] == 'audio':
      texts.append(f'<div class="text-input"><small>Input: {e(item["label"])}</small>'
                   f'<audio controls src="{e(item["file"])}"></audio></div>')
    else:
      texts.append(f'<div class="text-input"><small>Input: {e(item["label"])}</small>{e(item["value"])}</div>')
  for path in r.get('outputs', []):
    if path.endswith('.wav'):
      texts.append(f'<div class="text-input"><small>Written by the sample: {e(os.path.basename(path))}</small>'
                   f'<audio controls src="{e(path)}"></audio></div>')
    else:
      media.append(_figure(path, 'Written by the sample: ' + os.path.basename(path)))
  if texts:
    parts.append(''.join(texts))
  if media:
    parts.append(f'<div class="media">{"".join(media)}</div>')

  checks = r['verification']['checks']
  if checks:
    rows = ''.join(
        f'<tr class="{"" if c["pass"] else "bad"}"><td class="mark">{"&#10003;" if c["pass"] else "&#10007;"}</td>'
        f'<td>{e(c["name"])}</td><td>{e(c["expected"])}</td><td>{e(c["actual"])}</td></tr>' for c in checks)
    parts.append(f'<h4>Output checks ({e(r["verification"]["summary"])})</h4>'
                 f'<table><tr><th></th><th>Check</th><th>Expected</th><th>Actual</th></tr>{rows}</table>')
  if r['delegate'] == 'GPU':
    evidence = r.get('gpuEvidence') or []
    shown = '\n'.join(evidence) if evidence else 'none'
    parts.append(f'<h4>Sign of the GPU delegate in the native log</h4><pre>{e(shown)}</pre>')
  if r.get('baseline'):
    parts.append(f'<p>Output compared with the baseline run: <b>{e(r["baseline"])}</b></p>')
  links = ' &middot; '.join(f'<a href="{e(path)}">{e(name)}</a>' for name, path in r.get('logs', {}).items())
  parts.append(f'<p>{links}</p><p><small>Command: <code>{e(r.get("command", ""))}</code></small></p>')

  title = f'{r["domain"]}/{r["task"]} &middot; {e(r["modelId"])} &middot; {e(r["mode"])} &middot; {r["delegate"]}'
  return (f'<details class="case"><summary><span class="badge {r["status"]}">{r["status"]}</span>'
          f'<span class="title">{title}</span><span class="why">{e(r["details"])}</span>'
          f'<span class="time">{r["durationSec"]:.1f}s</span></summary><div class="body">{"".join(parts)}</div>'
          '</details>')


def format_html(state):
  e = html.escape
  results = state['results']
  counts = summarize_counts(results)
  ok = state['passed']
  env_items = ''.join(f'<li>{e(line)}</li>' for line in _environment_lines(state))
  count_items = ''.join(
      f'<span><span class="badge {status}">{status}</span> {counts[status]} &ndash; {e(STATUSES[status][1])}</span>'
      for status in ORDER if counts.get(status))
  baseline = ''
  if state.get('baseline'):
    b = state['baseline']
    baseline = (f'<p>Compared with <code>{e(b["path"])}</code>: {b["same"]} identical, {b["different"]} different, '
                f'{b["new"]} not in the baseline.</p>')
  # Cases that need attention first, then in the order they ran.
  ranked = sorted(enumerate(results), key=lambda pair: (ORDER.index(pair[1]['status']), pair[0]))
  cases = ''.join(_case_html(r) for _, r in ranked)
  return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<title>MediaPipe Python release validation: {e(str(state['environment'].get('mediapipeVersion')))}</title>
<style>{_CSS}</style></head><body>
<h1>MediaPipe Python release validation</h1>
<ul class="env">{env_items}</ul>
<div class="verdict {'ok' if ok else 'bad'}">{e(state['verdict'])}</div>
<div class="counts">{count_items}</div>
{baseline}
<h2>Cases ({counts['total']})</h2>
{cases}
<p><small>Everything shown here is also in <a href="status.json">status.json</a>; the installed packages are listed in
<a href="pip-freeze.txt">pip-freeze.txt</a>.</small></p>
</body></html>
"""


def write_report_files(report_dir, state):
  os.makedirs(report_dir, exist_ok=True)
  with open(os.path.join(report_dir, 'status.json'), 'w') as f:
    json.dump(state, f, indent=1, ensure_ascii=False)
    f.write('\n')
  with open(os.path.join(report_dir, 'report.md'), 'w') as f:
    f.write(format_markdown(state))
  with open(os.path.join(report_dir, 'index.html'), 'w') as f:
    f.write(format_html(state))
