"""Transparent, directly testable accuracy definitions."""

def summarize(records):
    """One record per attempted image; crop failures are incorrect attempts."""
    n = len(records)
    if n == 0:
        raise ValueError('Cannot calculate accuracy on zero photos')
    correct = sum(x['predicted'] == x['label'] for x in records)
    failures = sum(bool(x.get('detection_failed', False)) for x in records)
    groups = {}
    for bg in ['plain', 'cluttered']:
        subset = [r for r in records if r.get('background') == bg]
        if subset:
            groups[bg] = {'images': len(subset), 'correct': sum(r['predicted'] == r['label'] for r in subset),
                          'top1_accuracy_pct': round(100 * sum(r['predicted'] == r['label'] for r in subset) / len(subset), 2)}
    return {'images_evaluated': n, 'correct': correct,
            'top1_accuracy_pct': round(100 * correct / n, 2),
            'detection_failures': failures,
            'detection_failure_rate_pct': round(100 * failures / n, 2),
            'by_background': groups,
            'background_metadata_complete': all(r.get('background') in ('plain','cluttered') for r in records)}


def normalized_square_box(xys, image_size, margin=0.25):
    """Return integer bounding rectangle around normalized landmarks with margin."""
    if not xys:
        return None
    w, h = image_size
    xs, ys = [p[0] for p in xys], [p[1] for p in xys]
    xmin, xmax, ymin, ymax = min(xs), max(xs), min(ys), max(ys)
    side = max((xmax - xmin) * w, (ymax - ymin) * h) * (1 + 2 * margin)
    side = max(side, 2)
    cx, cy = (xmin + xmax) * w / 2, (ymin + ymax) * h / 2
    left = max(0, int(round(cx - side / 2)))
    top = max(0, int(round(cy - side / 2)))
    right = min(w, int(round(cx + side / 2)))
    bottom = min(h, int(round(cy + side / 2)))
    if right - left < 2 or bottom - top < 2:
        return None
    return (left, top, right, bottom)
