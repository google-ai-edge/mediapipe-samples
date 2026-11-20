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
"""Writes the validation report: status.json, report.md and index.html.

The same report is written for every platform: for the Python samples by
run_local.py, and for the observations of another runner by
verify_observations.py.
"""

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

PLATFORM_NAMES = {'python': 'Python', 'android': 'Android', 'ios': 'iOS'}


def _platform(state):
  return state.get('platform', 'python')


def _platform_name(state):
  return PLATFORM_NAMES.get(_platform(state), _platform(state))


def _gpu_name(state):
  """What the GPU delegate is called where the evidence for it was found."""
  return 'Metal' if _platform(state) == 'python' else 'GPU'


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


def _status_class(status):
  return status.lower().replace('_', '-')


def _environment_lines(state):
  env = state['environment']
  times = f"Started: {state['startedAt']}, finished: {state['finishedAt']}"
  if _platform(state) != 'python':
    # Whatever the runner of the platform reported about itself.
    return [f'{key}: {value}' for key, value in env.items()] + [times]
  lines = [
      f"MediaPipe under test: {env.get('mediapipeVersion') or 'not installed'} (requested: {env.get('mediapipeSpec')})",
      f"Python: {env.get('pythonVersion')}",
      f"System: {env.get('system')}",
      f"Processor: {env.get('processor')}",
      f"GPU: {env.get('gpu')}",
      f"Samples: commit {env.get('gitCommit')}{' with local changes' if env.get('gitDirty') else ''}",
      times,
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
  lines = [f'# MediaPipe {_platform_name(state)} release validation', '']
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
            '| # | Task | Model | Mode | Delegate | Status | Output checks | Seconds | Outputs | Details |',
            '|---|---|---|---|---|---|---|---|---|---|']
  for index, r in enumerate(results):
    outputs = ', '.join(f'`{os.path.basename(p)}`' for p in r.get('outputs', [])) or '-'
    lines.append(
        f"| {index + 1} | {_md(r['domain'] + '/' + r['task'])} | {_md(r['modelId'])} | {_md(r['mode'])} | "
        f"{r['delegate']} | {status_icon(r['status'])} | {_md(r['verification']['summary'])} | "
        f"{r['durationSec']:.1f} | {outputs} | {_md(r['details'][:500])} |")
  return '\n'.join(lines) + '\n'


_CSS = """
body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 24px;
       background: #f8fafc; color: #0f172a; }
h1 { margin: 0 0 8px; font-size: 24px; }
a { color: #0969da; text-decoration: none; }
a:hover { text-decoration: underline; }
.meta { color: #475569; font-size: 13px; line-height: 1.55; margin-bottom: 14px; }
.pills { margin-top: 6px; display: flex; gap: 6px; flex-wrap: wrap; }
.pkg-pill { display: inline-block; background: #e2e8f0; color: #1e293b; border-radius: 4px; padding: 2px 8px;
            font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
.verdict { font-size: 15px; font-weight: 600; padding: 10px 14px; border-radius: 8px; display: inline-block;
           margin-bottom: 14px; border: 1px solid transparent; }
.verdict.ok { background: #dcfce7; color: #166534; border-color: #bbf7d0; }
.verdict.bad { background: #fee2e2; color: #991b1b; border-color: #fecaca; }
.cards { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
.card { background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px 18px; min-width: 118px; }
.card .num { font-size: 24px; font-weight: 700; font-variant-numeric: tabular-nums; }
.card .lbl { font-size: 11px; color: #64748b; text-transform: uppercase; letter-spacing: 0.04em; margin-top: 2px; }
.view-toggle { margin: 16px 0 0; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.view-toggle button { padding: 8px 16px; border: 1px solid #0f172a; background: #fff; color: #0f172a;
                      border-radius: 6px; cursor: pointer; font-weight: 700; font-size: 13px; }
.view-toggle button.active { background: #0f172a; color: #fff; }
.view-toggle .hint { color: #64748b; font-size: 12px; }
.filter-bar { margin: 14px 0; display: flex; gap: 16px; flex-wrap: wrap; align-items: center; }
.filter-group { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
.filter-group .group-lbl { font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase;
                           margin-right: 2px; }
.filter-group button { padding: 5px 11px; border: 1px solid #cbd5e1; background: #fff; color: #1e293b;
                       border-radius: 6px; cursor: pointer; font-weight: 600; font-size: 12px; }
.filter-group button:hover { background: #f1f5f9; }
.filter-group button.active { background: #0f172a; color: #fff; border-color: #0f172a; }
.badge { padding: 2px 8px; border-radius: 999px; font-weight: 700; font-size: 11px; display: inline-block;
         white-space: nowrap; }
.badge-pass { background: #dcfce7; color: #166534; }
.badge-fail { background: #fee2e2; color: #991b1b; }
.badge-gpu-unconfirmed { background: #fef3c7; color: #92400e; }
.badge-unsupported { background: #e2e8f0; color: #475569; }
.badge-unverified { background: #e0e7ff; color: #3730a3; }

/* Table view */
table.matrix { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid #e2e8f0; font-size: 13px; }
table.matrix th, table.matrix td { border-bottom: 1px solid #e2e8f0; padding: 8px 10px; text-align: left;
                                   vertical-align: top; }
table.matrix th { background: #f1f5f9; position: sticky; top: 0; z-index: 1; color: #334155; font-weight: 600; }
table.matrix tbody tr:hover { background: #f8fafc; }
.num-col { color: #64748b; font-variant-numeric: tabular-nums; white-space: nowrap; }
.checks { max-width: 380px; word-break: break-word; }
.checks ul { margin: 4px 0 0; padding-left: 16px; }
.checks li.ok { color: #166534; }
.checks li.bad { color: #991b1b; font-weight: 600; }
.details { max-width: 340px; word-break: break-word; color: #334155; }
.thumb-cell { min-width: 190px; }
.thumbs { display: flex; gap: 6px; flex-wrap: wrap; align-items: flex-start; }
.thumbs img { width: 140px; max-height: 110px; object-fit: contain; background: #f8fafc;
              border: 1px solid #cbd5e1; border-radius: 4px; display: block; }
.thumbs audio { width: 210px; height: 32px; display: block; }
.review-link { display: inline-block; margin-top: 4px; font-size: 12px; font-weight: 600; color: #0f172a;
               background: #f1f5f9; border: 1px solid #cbd5e1; border-radius: 4px; padding: 2px 8px; cursor: pointer; }
.review-link:hover { background: #e2e8f0; text-decoration: none; }

/* Review mode */
#review-view { display: none; }
body.review #review-view { display: block; }
body.review #table-view { display: none; }
.review-nav { display: flex; gap: 10px; align-items: center; margin: 12px 0; flex-wrap: wrap; }
.review-nav button { padding: 9px 20px; border: 1px solid #0f172a; background: #0f172a; color: #fff;
                     border-radius: 6px; cursor: pointer; font-weight: 700; font-size: 14px; }
.review-nav button.secondary { background: #fff; color: #0f172a; }
.review-nav button:disabled { opacity: 0.35; cursor: default; }
.review-nav .position { font-weight: 700; font-variant-numeric: tabular-nums; }
.review-nav select { padding: 8px 10px; border: 1px solid #cbd5e1; border-radius: 6px; background: #fff;
                     color: #0f172a; font-size: 13px; max-width: 480px; }
.review-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 18px 22px;
               display: grid; grid-template-columns: minmax(340px, 2fr) minmax(380px, 3fr); gap: 26px; }
@media (max-width: 920px) { .review-card { grid-template-columns: 1fr; } }
.review-card h2 { margin: 0 0 6px; font-size: 18px; word-break: break-word; }
.review-card h3 { margin: 16px 0 6px; font-size: 11px; color: #64748b; text-transform: uppercase;
                  letter-spacing: 0.05em; }
.review-card .sub { color: #475569; font-size: 13px; margin-bottom: 8px; display: flex; gap: 8px;
                    align-items: center; flex-wrap: wrap; }
.review-card .description { font-size: 14px; line-height: 1.5; color: #1e293b; }
.review-note { background: #fef9c3; border: 1px solid #fde047; border-radius: 6px; padding: 8px 10px;
               font-size: 13px; margin: 8px 0; }
.review-inputs { display: flex; gap: 12px; flex-wrap: wrap; align-items: flex-start; }
.review-inputs figure { margin: 0; }
.review-inputs img { width: 175px; max-height: 160px; object-fit: contain; background: #f8fafc;
                     border: 1px solid #cbd5e1; border-radius: 4px; display: block; }
.review-inputs figcaption, .review-inputs .label { font-size: 12px; color: #475569; margin-top: 4px; max-width: 220px; }
.review-inputs blockquote { margin: 0; padding: 8px 12px; background: #f1f5f9; border-left: 3px solid #94a3b8;
                            font-size: 13px; max-width: 440px; border-radius: 0 4px 4px 0; white-space: pre-wrap; }
.review-inputs audio { display: block; width: 290px; }
.review-checks { list-style: none; margin: 4px 0 0; padding: 0; font-size: 13px; }
.review-checks li { padding: 4px 0; border-bottom: 1px solid #f1f5f9; }
.review-checks li.ok::before { content: "\\2713  "; color: #166534; font-weight: 700; }
.review-checks li.bad::before { content: "\\2717  "; color: #991b1b; font-weight: 700; }
.review-checks li.bad { color: #991b1b; font-weight: 600; background: #fef2f2; padding-left: 4px; }
.review-checks .values { color: #475569; font-weight: 400; }
.review-error { background: #fef2f2; border: 1px solid #fecaca; color: #991b1b; font-weight: 600;
                font-size: 13px; padding: 8px 10px; border-radius: 6px; white-space: pre-wrap; margin: 6px 0; }
.review-shot h3:first-child { margin-top: 0; }
.shot-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 8px; }
.shot-tabs button { padding: 4px 10px; border: 1px solid #cbd5e1; background: #fff; color: #1e293b;
                    border-radius: 5px; cursor: pointer; font-size: 12px; font-weight: 600; }
.shot-tabs button.active { background: #0f172a; color: #fff; border-color: #0f172a; }
.main-shot-wrap { background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 6px; padding: 8px;
                  text-align: center; }
.main-shot-wrap img { max-width: 100%; max-height: 540px; height: auto; border-radius: 4px; display: inline-block; }
.main-shot-caption { font-size: 12px; color: #475569; margin-top: 6px; }
.review-steps { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 10px;
                margin-top: 10px; }
.review-steps figure { margin: 0; cursor: pointer; border: 2px solid transparent; border-radius: 6px; padding: 4px;
                       background: #f8fafc; }
.review-steps figure.selected { border-color: #0f172a; background: #f1f5f9; }
.review-steps img { width: 100%; max-height: 140px; object-fit: contain; border-radius: 4px; display: block; }
.review-steps figcaption { font-size: 12px; color: #475569; margin-top: 4px; text-align: center; }
.audio-output-card { background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 6px; padding: 12px 14px;
                     margin-bottom: 10px; }
.audio-output-card audio { width: 100%; max-width: 420px; display: block; margin: 6px 0; }
.heard-quote { margin-top: 8px; padding: 8px 10px; background: #dcfce7; border-left: 3px solid #166534;
               color: #14532d; font-size: 13px; border-radius: 0 4px 4px 0; }
.unsupported-box { background: #f1f5f9; border: 1px solid #cbd5e1; border-radius: 6px; padding: 14px 16px;
                   color: #334155; font-size: 13px; line-height: 1.5; }
.review-card details { margin-top: 10px; font-size: 12px; }
.review-card summary { cursor: pointer; font-weight: 600; color: #334155; }
.review-card pre { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 8px 10px;
                   overflow: auto; max-height: 280px; white-space: pre-wrap; font-size: 12px; margin: 6px 0 0; }
.cmd-line { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11.5px; background: #f1f5f9;
            padding: 6px 8px; border-radius: 4px; word-break: break-all; margin-top: 6px; }
.footer { margin-top: 20px; color: #64748b; font-size: 12px; }
"""

_SCRIPT = """
var cases = JSON.parse(document.getElementById('cases-data').textContent);
var statusFilter = 'ALL';
var domainFilter = 'ALL';
var delegateFilter = 'ALL';
var reviewIndex = 0;
var selectedShotIndex = 0;

function applyTableFilters() {
  document.querySelectorAll('#table-view tbody tr').forEach(function (tr) {
    var okStatus = statusFilter === 'ALL' || tr.dataset.status === statusFilter;
    var okDomain = domainFilter === 'ALL' || tr.dataset.domain === domainFilter;
    var okDelegate = delegateFilter === 'ALL' || tr.dataset.delegate === delegateFilter;
    tr.style.display = (okStatus && okDomain && okDelegate) ? '' : 'none';
  });
}

function setFilter(kind, value, btn) {
  btn.parentElement.querySelectorAll('button').forEach(function (b) { b.classList.remove('active'); });
  btn.classList.add('active');
  if (kind === 'status') statusFilter = value;
  else if (kind === 'domain') domainFilter = value;
  else if (kind === 'delegate') delegateFilter = value;
  applyTableFilters();
  reviewIndex = 0;
  selectedShotIndex = 0;
  if (document.body.classList.contains('review')) renderReview();
}

function setView(view) {
  var isReview = view === 'review';
  document.body.classList.toggle('review', isReview);
  document.getElementById('show-table').classList.toggle('active', !isReview);
  document.getElementById('show-review').classList.toggle('active', isReview);
  if (history.replaceState) {
    history.replaceState(null, '', isReview ? '#review' : location.pathname + location.search);
  }
  if (isReview) renderReview();
}

function visibleCases() {
  return cases.filter(function (c) {
    return (statusFilter === 'ALL' || c.status === statusFilter) &&
           (domainFilter === 'ALL' || c.domain === domainFilter) &&
           (delegateFilter === 'ALL' || c.delegate === delegateFilter);
  });
}

function openCaseInReview(caseId) {
  var list = visibleCases();
  var idx = list.findIndex(function (c) { return c.id === caseId; });
  if (idx < 0) {
    statusFilter = 'ALL';
    domainFilter = 'ALL';
    delegateFilter = 'ALL';
    document.querySelectorAll('.filter-group').forEach(function (g) {
      var buttons = g.querySelectorAll('button');
      buttons.forEach(function (b, i) { b.classList.toggle('active', i === 0); });
    });
    applyTableFilters();
    list = visibleCases();
    idx = list.findIndex(function (c) { return c.id === caseId; });
  }
  reviewIndex = Math.max(0, idx);
  selectedShotIndex = 0;
  setView('review');
  window.scrollTo(0, 0);
}

function stepReview(delta) {
  var count = visibleCases().length;
  if (count === 0) return;
  var next = Math.min(count - 1, Math.max(0, reviewIndex + delta));
  if (next !== reviewIndex) {
    reviewIndex = next;
    selectedShotIndex = 0;
    renderReview();
  }
}

function jumpReview(indexStr) {
  var idx = parseInt(indexStr, 10);
  if (!isNaN(idx)) {
    reviewIndex = idx;
    selectedShotIndex = 0;
    renderReview();
  }
}

// Builds an element; text is always set via textContent, never innerHTML.
function el(tag, className, text) {
  var node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = String(text);
  return node;
}

function basename(p) {
  var parts = String(p || '').split('/');
  return parts[parts.length - 1] || p;
}

function renderInput(input) {
  var wrap;
  if (input.kind === 'image') {
    wrap = el('figure');
    var link = el('a');
    link.href = input.file;
    link.target = '_blank';
    var img = el('img');
    img.src = input.file;
    img.alt = input.label || '';
    link.appendChild(img);
    wrap.appendChild(link);
    wrap.appendChild(el('figcaption', '', input.label));
  } else if (input.kind === 'audio') {
    wrap = el('div');
    var audio = el('audio');
    audio.controls = true;
    audio.src = input.file;
    wrap.appendChild(audio);
    wrap.appendChild(el('div', 'label', input.label));
  } else {
    wrap = el('div');
    wrap.appendChild(el('blockquote', '', input.value));
    wrap.appendChild(el('div', 'label', input.label));
  }
  return wrap;
}

function renderReview() {
  var list = visibleCases();
  var card = document.getElementById('review-card');
  var select = document.getElementById('review-select');
  card.textContent = '';
  select.textContent = '';

  if (reviewIndex >= list.length) reviewIndex = Math.max(0, list.length - 1);
  document.getElementById('review-prev').disabled = reviewIndex <= 0;
  document.getElementById('review-next').disabled = reviewIndex >= list.length - 1;

  if (list.length === 0) {
    document.getElementById('review-position').textContent = 'No matching cases';
    select.style.display = 'none';
    card.appendChild(el('div', 'sub', 'No cases match the active filter.'));
    return;
  }
  select.style.display = '';
  list.forEach(function (item, idx) {
    var opt = el('option', '', (idx + 1) + '. [' + item.status + '] ' + item.domain + '/' + item.task +
                 ' \\u00b7 ' + item.modelId + ' \\u00b7 ' + item.mode + ' \\u00b7 ' + item.delegate);
    opt.value = String(idx);
    if (idx === reviewIndex) opt.selected = true;
    select.appendChild(opt);
  });

  var c = list[reviewIndex];
  document.getElementById('review-position').textContent = 'Case ' + (reviewIndex + 1) + ' of ' + list.length;

  // --- Left column: metadata, inputs, checks, logs ---
  var left = el('div');
  left.appendChild(el('h2', '', c.domain + '/' + c.task + ' \\u00b7 ' + c.modelId + ' \\u00b7 ' + c.mode));

  var sub = el('div', 'sub');
  sub.appendChild(el('span', 'badge badge-' + c.status.toLowerCase().replace(/_/g, '-'), c.status));
  var gpuInfo = c.delegate === 'GPU'
    ? (c.gpuEvidence && c.gpuEvidence.length ? 'GPU (' + gpuName + ' confirmed)' : 'GPU')
    : 'CPU';
  sub.appendChild(el('span', '', 'Delegate: ' + gpuInfo + ' \\u00b7 ' + c.durationSec.toFixed(1) + 's'));
  if (c.baseline) {
    sub.appendChild(el('span', 'pkg-pill', 'Baseline: ' + c.baseline));
  }
  left.appendChild(sub);

  if (c.note) {
    left.appendChild(el('div', 'review-note', 'Note: ' + c.note));
  }

  left.appendChild(el('h3', '', 'What this case does'));
  left.appendChild(el('div', 'description', c.description || ''));

  left.appendChild(el('h3', '', 'Input'));
  var inputs = el('div', 'review-inputs');
  (c.inputs || []).forEach(function (inp) { inputs.appendChild(renderInput(inp)); });
  if (!inputs.childNodes.length) inputs.appendChild(el('div', 'label', 'No input recorded.'));
  left.appendChild(inputs);

  left.appendChild(el('h3', '', 'Output checks'));
  if (c.error) {
    left.appendChild(el('div', 'review-error', c.error));
  }
  if (c.traceback) {
    var tb = el('details');
    tb.appendChild(el('summary', '', 'Traceback'));
    tb.appendChild(el('pre', '', c.traceback));
    left.appendChild(tb);
  }
  var verification = c.verification || {};
  left.appendChild(el('div', 'sub', verification.summary && verification.summary !== '-'
    ? verification.summary + ' passed'
    : (c.status === 'UNSUPPORTED' ? c.details : 'No output checks ran.')));
  var checks = el('ul', 'review-checks');
  (verification.checks || []).forEach(function (check) {
    var li = el('li', check.pass ? 'ok' : 'bad', check.name);
    li.appendChild(el('span', 'values', ' \\u2014 expected ' + check.expected + ', got ' + check.actual));
    checks.appendChild(li);
  });
  left.appendChild(checks);

  if (c.delegate === 'GPU' && c.gpuEvidence && c.gpuEvidence.length) {
    left.appendChild(el('h3', '', 'GPU delegate evidence in native log'));
    left.appendChild(el('pre', '', c.gpuEvidence.join('\\n')));
  }

  left.appendChild(el('h3', '', 'Command & logs'));
  var logLinks = el('div', 'sub');
  var logEntries = Object.entries(c.logs || {});
  logEntries.forEach(function (pair, i) {
    if (i > 0) logLinks.appendChild(document.createTextNode(' \\u00b7 '));
    var a = el('a', '', pair[0]);
    a.href = pair[1];
    a.target = '_blank';
    logLinks.appendChild(a);
  });
  left.appendChild(logLinks);
  if (c.command) {
    left.appendChild(el('div', 'cmd-line', c.command));
  }

  if (c.runs && c.runs.length) {
    var raw = el('details');
    raw.appendChild(el('summary', '', 'Raw observed output (JSON)'));
    raw.appendChild(el('pre', '', JSON.stringify(c.runs, null, 2)));
    left.appendChild(raw);
  }

  // --- Right column: visual/audio/printed outputs ---
  var right = el('div', 'review-shot');
  var images = (c.outputs || []).filter(function (p) { return p.endsWith('.png'); });
  var audios = (c.outputs || []).filter(function (p) { return p.endsWith('.wav'); });

  if (images.length > 0) {
    right.appendChild(el('h3', '', images.length > 1
      ? 'Output images written by the sample (' + images.length + ')'
      : 'Output image written by the sample'));
    if (selectedShotIndex >= images.length) selectedShotIndex = 0;

    if (images.length > 1) {
      var tabs = el('div', 'shot-tabs');
      images.forEach(function (imgPath, idx) {
        var btn = el('button', idx === selectedShotIndex ? 'active' : '',
                     (idx + 1) + '. ' + basename(imgPath));
        btn.onclick = function () { selectedShotIndex = idx; renderReview(); };
        tabs.appendChild(btn);
      });
      right.appendChild(tabs);
    }

    var activeImg = images[selectedShotIndex];
    var mainWrap = el('div', 'main-shot-wrap');
    var mainLink = el('a');
    mainLink.href = activeImg;
    mainLink.target = '_blank';
    var mainImg = el('img');
    mainImg.src = activeImg;
    mainImg.alt = basename(activeImg);
    mainLink.appendChild(mainImg);
    mainWrap.appendChild(mainLink);
    mainWrap.appendChild(el('div', 'main-shot-caption', basename(activeImg) + ' (click to open full size)'));
    right.appendChild(mainWrap);

    if (images.length > 1) {
      var gallery = el('div', 'review-steps');
      images.forEach(function (imgPath, idx) {
        var fig = el('figure', idx === selectedShotIndex ? 'selected' : '');
        fig.onclick = function () { selectedShotIndex = idx; renderReview(); };
        var thumb = el('img');
        thumb.src = imgPath;
        thumb.alt = basename(imgPath);
        fig.appendChild(thumb);
        fig.appendChild(el('figcaption', '', (idx + 1) + '. ' + basename(imgPath)));
        gallery.appendChild(fig);
      });
      right.appendChild(gallery);
    }
  }

  if (audios.length > 0) {
    right.appendChild(el('h3', '', 'Synthesized audio written by the sample'));
    audios.forEach(function (wavPath) {
      var box = el('div', 'audio-output-card');
      box.appendChild(el('div', 'sub', basename(wavPath)));
      var player = el('audio');
      player.controls = true;
      player.src = wavPath;
      box.appendChild(player);
      var heard = c.runs && c.runs[0] && c.runs[0].data && c.runs[0].data.heard;
      if (heard) {
        box.appendChild(el('div', 'heard-quote', 'Heard by AudioTranscriber (ASR round-trip): "' + heard + '"'));
      }
      right.appendChild(box);
    });
  }

  if (c.stdout && c.stdout.trim()) {
    right.appendChild(el('h3', '', 'Sample console output'));
    right.appendChild(el('pre', '', c.stdout.trim()));
  } else if (images.length === 0 && audios.length === 0) {
    right.appendChild(el('h3', '', 'Sample output'));
    if (c.status === 'UNSUPPORTED') {
      right.appendChild(el('div', 'unsupported-box', c.details));
    } else if (c.error) {
      right.appendChild(el('div', 'review-error', c.error));
    } else {
      right.appendChild(el('div', 'unsupported-box', 'No visual or console output recorded for this case.'));
    }
  }

  card.appendChild(left);
  card.appendChild(right);
}

document.addEventListener('keydown', function (event) {
  if (!document.body.classList.contains('review')) return;
  var tag = (event.target && event.target.tagName) || '';
  if (tag === 'SELECT' || tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'AUDIO') return;
  if (event.key === 'ArrowRight' || event.key === ' ' || event.key === 'j') {
    event.preventDefault();
    stepReview(1);
  } else if (event.key === 'ArrowLeft' || event.key === 'k') {
    event.preventDefault();
    stepReview(-1);
  } else if (event.key >= '1' && event.key <= '9') {
    var idx = parseInt(event.key, 10) - 1;
    var list = visibleCases();
    var cur = list[reviewIndex];
    var imgs = cur ? (cur.outputs || []).filter(function (p) { return p.endsWith('.png'); }) : [];
    if (idx < imgs.length) {
      selectedShotIndex = idx;
      renderReview();
    }
  } else if (event.key === 'Escape') {
    setView('table');
  }
});

if (location.hash === '#review') setView('review');
"""


def _read_stdout_fallback(report_dir, result):
  stdout = result.get('stdout')
  if stdout is not None:
    return stdout
  rel = (result.get('logs') or {}).get('sample output')
  if rel and report_dir:
    path = os.path.join(report_dir, rel)
    if os.path.isfile(path):
      with open(path, encoding='utf-8', errors='replace') as f:
        return f.read()[:8000]
  return ''


def _table_row_html(index, r, gpu_name='Metal'):
  e = html.escape
  status = r['status']
  badge = f'<span class="badge badge-{_status_class(status)}">{e(status)}</span>'
  if r['delegate'] == 'GPU':
    gpu_cell = gpu_name if r.get('gpuEvidence') else ('rejected' if status == 'UNSUPPORTED' else '-')
  else:
    gpu_cell = 'CPU'

  checks = r['verification']['checks']
  if checks:
    any_bad = any(not c['pass'] for c in checks)
    items = ''.join(
        f'<li class="{"ok" if c["pass"] else "bad"}">'
        f'{"&#10003;" if c["pass"] else "&#10007;"} {e(c["name"])}: '
        f'expected {e(str(c["expected"]))}, got {e(str(c["actual"]))}</li>'
        for c in checks)
    checks_html = (f'<details{" open" if any_bad else ""}>'
                   f'<summary>{e(r["verification"]["summary"])}</summary>'
                   f'<ul>{items}</ul></details>')
  else:
    checks_html = e(r['verification']['summary'])

  outputs = r.get('outputs', [])
  images = [p for p in outputs if p.endswith('.png')]
  audios = [p for p in outputs if p.endswith('.wav')]
  media_parts = []
  for img_path in images[:2]:
    media_parts.append(
        f'<a href="{e(img_path)}" target="_blank" title="{e(os.path.basename(img_path))}">'
        f'<img src="{e(img_path)}" loading="lazy" alt="{e(os.path.basename(img_path))}"></a>')
  if len(images) > 2:
    media_parts.append(f'<span class="pkg-pill">+{len(images) - 2} more</span>')
  for wav_path in audios[:1]:
    media_parts.append(f'<audio controls preload="none" src="{e(wav_path)}"></audio>')

  review_btn = (f'<button type="button" class="review-link" data-case-id="{e(r["id"])}" '
                'onclick="openCaseInReview(this.dataset.caseId)">Review &rarr;</button>')
  thumb_html = (f'<div class="thumbs">{"".join(media_parts)}</div>{review_btn}'
                if media_parts else review_btn)

  detail_text = r.get('error') or r.get('details') or ''
  if r.get('baseline') and r['baseline'] != 'identical':
    detail_text = f"{detail_text} [baseline: {r['baseline']}]".strip()

  return (
      f'<tr data-status="{e(status)}" data-domain="{e(r["domain"])}" data-delegate="{e(r["delegate"])}">'
      f'<td class="num-col">{index + 1}</td>'
      f'<td><code>{e(r["domain"] + "/" + r["task"])}</code></td>'
      f'<td><code>{e(r["modelId"])}</code></td>'
      f'<td><code>{e(r["mode"])}</code></td>'
      f'<td>{e(r["delegate"])}</td>'
      f'<td>{e(gpu_cell)}</td>'
      f'<td>{badge}</td>'
      f'<td class="num-col">{r["durationSec"]:.1f}s</td>'
      f'<td class="checks">{checks_html}</td>'
      f'<td class="details">{e(detail_text)}</td>'
      f'<td class="thumb-cell">{thumb_html}</td>'
      '</tr>'
  )


def format_html(state, report_dir=None):
  e = html.escape
  results = state['results']
  counts = summarize_counts(results)
  ok = state['passed']
  env = state['environment']

  # Cases that need attention come first when there are failures; otherwise keep matrix order
  # so CPU and GPU pairs of each task sit side by side for visual tabbing.
  has_failures = any(r['status'] in ('FAIL', 'GPU_UNCONFIRMED', 'UNVERIFIED') for r in results)
  if has_failures:
    ordered_results = [
        r for _, r in sorted(enumerate(results), key=lambda pair: (ORDER.index(pair[1]['status']), pair[0]))
    ]
  else:
    ordered_results = list(results)

  if _platform(state) == 'python':
    pills = [
        f'mediapipe@{env.get("mediapipeVersion") or "unknown"}',
        f'python@{env.get("pythonVersion") or "unknown"}',
        f'requested: {env.get("mediapipeSpec", "")}',
        'constraints: pinned' if env.get('constraints') else 'constraints: unpinned',
        f'commit@{env.get("gitCommit", "unknown")}{" (dirty)" if env.get("gitDirty") else ""}',
    ]
    machine = (f'<div>System: <code>{e(str(env.get("system")))}</code> | Processor: '
               f'<code>{e(str(env.get("processor")))}</code> | GPU: <code>{e(str(env.get("gpu")))}</code></div>')
  else:
    pills = [f'{key}: {value}' for key, value in env.items()]
    machine = ''
  pills_html = ''.join(f'<span class="pkg-pill">{e(p)}</span>' for p in pills)

  baseline_html = ''
  if state.get('baseline'):
    b = state['baseline']
    baseline_html = (
        f'<div>Compared with <code>{e(b["path"])}</code>: '
        f'<b>{b["same"]}</b> identical, <b>{b["different"]}</b> different, '
        f'<b>{b["new"]}</b> not in baseline</div>')

  card_defs = [
      ('Total', counts['total'], '#0f172a'),
      ('Pass', counts['PASS'], '#166534'),
      ('Unsupported', counts['UNSUPPORTED'], '#475569'),
      ('Fail', counts['FAIL'], '#991b1b'),
      ('GPU Unconfirmed', counts['GPU_UNCONFIRMED'], '#92400e'),
      ('Unverified', counts['UNVERIFIED'], '#3730a3'),
  ]
  cards_html = ''.join(
      f'<div class="card"><div class="num" style="color:{color}">{num}</div><div class="lbl">{e(lbl)}</div></div>'
      for lbl, num, color in card_defs
      if lbl in ('Total', 'Pass', 'Unsupported', 'Fail') or num > 0
  )

  status_buttons = [f'<button class="active" onclick="setFilter(\'status\', \'ALL\', this)">All ({counts["total"]})</button>']
  for status in ('PASS', 'UNSUPPORTED', 'FAIL', 'GPU_UNCONFIRMED', 'UNVERIFIED'):
    if counts.get(status) or status in ('PASS', 'FAIL'):
      label = status.replace('_', ' ').title() if status != 'GPU_UNCONFIRMED' else 'GPU Unconfirmed'
      status_buttons.append(
          f'<button onclick="setFilter(\'status\', \'{status}\', this)">{e(label)} ({counts[status]})</button>')

  domains_seen = list(dict.fromkeys(r['domain'] for r in ordered_results))
  domain_buttons = ['<button class="active" onclick="setFilter(\'domain\', \'ALL\', this)">All domains</button>'] + [
      f'<button onclick="setFilter(\'domain\', \'{e(d)}\', this)">{e(d)}</button>' for d in domains_seen
  ]

  delegates_seen = list(dict.fromkeys(r['delegate'] for r in ordered_results))
  delegate_buttons = ['<button class="active" onclick="setFilter(\'delegate\', \'ALL\', this)">All</button>'] + [
      f'<button onclick="setFilter(\'delegate\', \'{e(d)}\', this)">{e(d)}</button>' for d in delegates_seen
  ]

  rows_html = '\n'.join(_table_row_html(idx, r, _gpu_name(state)) for idx, r in enumerate(ordered_results))

  cases_payload = [
      {
          'id': r['id'],
          'domain': r['domain'],
          'task': r['task'],
          'modelId': r['modelId'],
          'mode': r['mode'],
          'delegate': r['delegate'],
          'status': r['status'],
          'durationSec': r['durationSec'],
          'description': r.get('description', ''),
          'details': r.get('details', ''),
          'note': r.get('note'),
          'error': r.get('error'),
          'traceback': r.get('traceback'),
          'baseline': r.get('baseline'),
          'inputs': r.get('inputs', []),
          'outputs': r.get('outputs', []),
          'verification': r.get('verification'),
          'gpuEvidence': r.get('gpuEvidence', []),
          'logs': r.get('logs', {}),
          'command': r.get('command', ''),
          'stdout': _read_stdout_fallback(report_dir, r),
          'runs': r.get('runs', []),
      }
      for r in ordered_results
  ]
  # Escape '<', '>' and '&' so embedded JSON can never close </script>.
  cases_json = (
      json.dumps(cases_payload, ensure_ascii=False)
      .replace('&', '\\u0026')
      .replace('<', '\\u003c')
      .replace('>', '\\u003e')
  )

  return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>MediaPipe {e(_platform_name(state))} Release Validation: {e(str(env.get('mediapipeVersion')))}</title>
  <style>{_CSS}</style>
</head>
<body>
  <h1>MediaPipe {e(_platform_name(state))} Release Validation Report</h1>
  <div class="meta">
    <div>Started: {e(state['startedAt'])} | Finished: {e(state['finishedAt'])}</div>
    {machine}
    {baseline_html}
    <div class="pills">{pills_html}</div>
  </div>
  <div class="verdict {'ok' if ok else 'bad'}">{e(state['verdict'])}</div>
  <div class="cards">{cards_html}</div>
  <div class="view-toggle">
    <button id="show-table" class="active" onclick="setView('table')">Table</button>
    <button id="show-review" onclick="setView('review')">Review cases one by one</button>
    <span class="hint">In review mode, use Next / Previous, &larr; / &rarr; arrow keys, or Space (1&ndash;9 switches output images, Esc returns to table). Filters apply to both views.</span>
  </div>
  <div class="filter-bar">
    <div class="filter-group"><span class="group-lbl">Status:</span>{''.join(status_buttons)}</div>
    <div class="filter-group"><span class="group-lbl">Domain:</span>{''.join(domain_buttons)}</div>
    <div class="filter-group"><span class="group-lbl">Delegate:</span>{''.join(delegate_buttons)}</div>
  </div>
  <div id="review-view">
    <div class="review-nav">
      <button class="secondary" id="review-prev" onclick="stepReview(-1)">&larr; Previous</button>
      <button id="review-next" onclick="stepReview(1)">Next &rarr;</button>
      <span class="position" id="review-position"></span>
      <select id="review-select" onchange="jumpReview(this.value)" aria-label="Jump to case"></select>
    </div>
    <div class="review-card" id="review-card"></div>
  </div>
  <div id="table-view">
    <table class="matrix">
      <thead>
        <tr>
          <th>#</th><th>Task</th><th>Model</th><th>Mode</th><th>Delegate</th><th>Actual</th><th>Status</th><th>Duration</th><th>Output checks</th><th>Details</th><th>Output</th>
        </tr>
      </thead>
      <tbody>
        {rows_html}
      </tbody>
    </table>
  </div>
  <div class="footer">
    Everything shown here is also in <a href="status.json">status.json</a> and <a href="report.md">report.md</a>;
    installed packages are listed in <a href="pip-freeze.txt">pip-freeze.txt</a>.
  </div>
  <script type="application/json" id="cases-data">{cases_json}</script>
  <script>var gpuName = {json.dumps(_gpu_name(state))};</script>
  <script>{_SCRIPT}</script>
</body>
</html>
"""


def write_report_files(report_dir, state):
  os.makedirs(report_dir, exist_ok=True)
  with open(os.path.join(report_dir, 'status.json'), 'w', encoding='utf-8') as f:
    json.dump(state, f, indent=1, ensure_ascii=False)
    f.write('\n')
  with open(os.path.join(report_dir, 'report.md'), 'w', encoding='utf-8') as f:
    f.write(format_markdown(state))
  with open(os.path.join(report_dir, 'index.html'), 'w', encoding='utf-8') as f:
    f.write(format_html(state, report_dir=report_dir))
