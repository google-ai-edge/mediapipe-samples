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
"""Searches images and texts with the MediaPipe Semantic Retriever task.

  pip install mediapipe
  python semantic_retriever.py --query "a man doing yoga on the beach"

The Semantic Retriever keeps an index of images and texts and finds the ones
that match a query by meaning. This sample indexes a few photos and two short
texts, then searches them. The model (about 390 MB) and the sample photos are
downloaded on first use. The best matches are printed with their scores.
"""

import argparse
import os
import urllib.request
from typing import List, Optional, Sequence

from mediapipe.tasks import python
from mediapipe.tasks.python import retrieval

MODEL_URL = 'https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/main/embeddinggemma-2-text-vision-440m.litertlm'

# The photos that are indexed by default, and where they come from.
IMAGE_URLS = {
    'burger.jpg': 'https://storage.googleapis.com/mediapipe-tasks/image_classifier/burger.jpg',
    'cat.jpg': 'https://storage.googleapis.com/mediapipe-tasks/image_classifier/cat.jpg',
    'yoga.jpg': 'https://storage.googleapis.com/mediapipe-assets/pose.jpg',
    'riders.jpg': 'https://storage.googleapis.com/mediapipe-assets/segmentation_input_rotation0.jpg',
    'portrait.jpg': 'https://storage.googleapis.com/mediapipe-assets/portrait.jpg',
}

# The texts that are indexed by default.
DOCUMENTS = [
    'The quarterly tax filing deadline is next Tuesday.',
    'Whisk the eggs, then fold in the flour and the melted butter.',
]

# The query that is searched by default.
QUERY = 'a man doing yoga on the beach'

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
        image_files: Sequence[str],
        documents: Sequence[str],
        query: str,
        limit: int = 3,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU
        ) -> List[retrieval.RetrievalResult]:
  """Indexes images and texts, and searches them.

  Args:
    model: Path of the embedding model.
    image_files: Paths of the images to index. The ID of an image is its file
      name.
    documents: The texts to index. Their IDs are "document 1", "document 2",
      and so on.
    query: The text to search for.
    limit: The maximum number of matches to return.
    delegate: The hardware to run the model on.

  Returns:
    The best matches of the query, best first.
  """
  # Create the embedder that turns images and texts into embeddings
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  embedder_options = retrieval.UniversalEmbedderOptions(
      base_options=base_options)

  with retrieval.UniversalEmbedder.create_from_options(
      embedder_options) as embedder:
    # The index stores embeddings of the size the model produces.
    dimension = len(embedder.embed_text(query).embeddings[0].embedding)

    # Create the retriever. Without a database path, the index is kept in
    # memory.
    retriever_options = retrieval.SemanticRetrieverOptions(
        embedder=embedder, embedding_dimension=dimension)

    with retrieval.SemanticRetriever.create_from_options(
        retriever_options) as retriever:
      # Index the images and the texts
      for image_file in image_files:
        retriever.insert_image(os.path.basename(image_file), image_file)
      for index, document in enumerate(documents):
        retriever.insert_document(f'document {index + 1}', document)
      print(f'Indexed {len(retriever.get_all_record_ids())} records')

      # Search. By default only matches with a similarity of at least 0.5 are
      # returned; here the best matches are returned whatever their score.
      retrieval_results = retriever.retrieve(
          query, limit=limit, min_similarity=0.0)

  # Print the matches
  print(f'Best matches for "{query}":')
  for result in retrieval_results:
    print(f'  {result.id} ({result.score:.2f})')

  return retrieval_results


def main(argv: Optional[Sequence[str]] = None
         ) -> List[retrieval.RetrievalResult]:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the embedding model. Downloaded if it is missing.',
      default='embeddinggemma-2-text-vision-440m.litertlm')
  parser.add_argument(
      '--images',
      help='Paths of the images to index. Without it, sample photos are '
      'downloaded.',
      nargs='+',
      default=None)
  parser.add_argument(
      '--documents',
      help='The texts to index.',
      nargs='*',
      default=DOCUMENTS)
  parser.add_argument('--query', help='The text to search for.', default=QUERY)
  parser.add_argument(
      '--limit',
      help='The maximum number of matches to print.',
      type=int,
      default=3)
  parser.add_argument(
      '--delegate',
      help='Hardware to run the model on.',
      choices=sorted(DELEGATES),
      default='cpu')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)
  image_files = args.images
  if image_files is None:
    image_files = list(IMAGE_URLS)
    for name, url in IMAGE_URLS.items():
      download(url, name)

  return run(args.model, image_files, args.documents, args.query, args.limit,
             DELEGATES[args.delegate])


if __name__ == '__main__':
  main()
