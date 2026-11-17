"""Fixed 26-way pretrained model, applied identically to both input variants."""
import torch
from . import MODEL_ID


class Classifier:
    def __init__(self, model_id=MODEL_ID):
        from transformers import AutoImageProcessor, AutoModelForImageClassification
        self.processor = AutoImageProcessor.from_pretrained(model_id)
        self.model = AutoModelForImageClassification.from_pretrained(model_id)
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        self.model.to(self.device).eval()
        self.id2label = {int(k):str(v).upper() for k, v in self.model.config.id2label.items()}
        if len(self.id2label) != 26:
            raise ValueError(f'Expected 26 outputs, found {len(self.id2label)}')

    @torch.inference_mode()
    def predict(self, photos, batch_size=16):
        results = []
        for i in range(0, len(photos), batch_size):
            batch = photos[i:i+batch_size]
            inputs = self.processor(images=batch, return_tensors='pt')
            inputs = {k:v.to(self.device) for k,v in inputs.items()}
            logits = self.model(**inputs).logits
            tops = logits.argmax(dim=-1).detach().cpu().tolist()
            results.extend([self.id2label[int(idx)] for idx in tops])
        return results
