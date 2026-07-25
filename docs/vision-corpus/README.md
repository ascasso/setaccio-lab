# Local Vision Corpus Contract

The controlled vision matrix uses one fixed, vision-specific local corpus. This
directory documents the contract; it does not contain source images or filled
case metadata.

## Local layout

Create the following ignored layout from the repository root:

```text
setaccio-lab/local/vision-corpus/
├── cases.json
└── images/
    ├── vision-single-subject.jpg
    ├── vision-complex-scene.jpg
    ├── vision-text-heavy.jpg
    ├── vision-low-quality.jpg
    ├── vision-ambiguous.jpg
    └── vision-file-organization.jpg
```

Copy
[`cases.template.json`](../../setaccio-lab/src/main/resources/vision-corpus/cases.template.json)
to the ignored directory as `cases.json`, then replace every placeholder. The
six cases are a target rather than a gate: remove a case if no clearly safe,
meaningfully different image is available.

The entire `setaccio-lab/local/vision-corpus/` directory is ignored. It may
contain private observations and must not be forced into Git.

## Case contract

The catalog is vision-specific and has `corpusVersion` `1`. Every case records:

- `caseId`: a stable, non-sensitive lowercase identifier;
- `imageFile`: a relative `images/<caseId>.<extension>` path;
- `mimeType`: the detected image MIME type;
- `blake3`: the 64-character lowercase BLAKE3 digest of the exact input bytes;
- `referenceObservation`: a short human-authored account of visible content;
- `expectedConcepts`: important concepts a useful response should observe;
- `unsupportedDetails`: details unsupported by the image that a model must not
  invent;
- `limitations`: deliberate ambiguity, blur, low light, cropping, or other
  quality constraints;
- `privacyReview`: explicit sensitive-content, EXIF/GPS, and tracking-approval
  state.

Use the case ID as the copied image filename. Do not put an original filename,
absolute path, person name, address, account identifier, or other sensitive
detail in the catalog. The future matrix may use the local observation fields
for human review, but public evidence must use only safe case IDs and
public-safe aggregate findings.

The MIME type and BLAKE3 digest describe the exact bytes used by the matrix.
Recalculate both whenever an image is resized, recompressed, stripped of
metadata, or otherwise changed.

## Privacy review

Before adding a case:

1. Review the image for private people, addresses, documents, screens,
   credentials, and other sensitive content.
2. Prefer a different image whenever safety is uncertain.
3. Keep `approvedForTracking` false for private corpus material.

Before any image or derivative is made public:

1. Strip EXIF and GPS metadata from the candidate derivative.
2. Recheck its visible content and metadata after transformation.
3. Set the review fields only after those checks are complete.
4. Obtain explicit user approval for the exact file before changing ignore
   rules, staging it, or committing it.

A true review field documents a completed check; it does not itself authorize
tracking. Private source images remain ignored even when a derivative is later
approved.

## Scope boundary

This contract is not a generalized suite loader or prompt-management format.
Slice 5 may add a dedicated vision matrix reader and validation for this exact
layout. It must not search arbitrary directories, infer cases from filenames,
or copy private metadata into public artifacts.
