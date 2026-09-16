# Bundled Image Provenance

Updated: 2026-09-17

## Scope

This record covers the four grayscale `Change bed linen` images first bundled
with Visual Routines. The images were generated as project assets outside the
app. Visual Routines does not generate images at runtime.

The accepted files are 1448 x 1086 pixel, 4:3, 8-bit sRGB PNG images. They were
copied into `app/src/main/res/drawable-nodpi` byte-for-byte without resizing,
cropping, recompression, or other post-generation processing.

## Generation Source And Settings

- Tool: the OpenAI image generation tool exposed to Codex as `image_gen`.
- Generation dates: the original set was generated on 2026-07-29; the accepted
  duvet-insertion arrow clarity revision was generated on 2026-08-15.
- Model record: the tool response did not expose a public API model identifier.
  The embedded C2PA data identifies the software agent as `gpt-image` version
  `2.0`, the claim generator as `OpenAI Media Service API`, and the digital
  source type as `trainedAlgorithmicMedia`.
- Settings: no explicit quality, size, seed, or sampling settings were exposed
  or supplied. Each accepted file was produced at 1448 x 1086 pixels.
- Edit method: each action arrow was added through a generative image edit.
  The duvet-insertion arrow received one later generative clarity revision.
  The finished-state image was also edited once to remove an unwanted heading
  generated despite the base prompt.

The model description above deliberately distinguishes the metadata that is
present from an API model name that was not available in the tool record.

## Accepted Assets

| Stable asset id | Repository file | Source ancestry | SHA-256 |
| --- | --- | --- | --- |
| `bed-linen-remove-sheet` | `bed_linen_remove_sheet.png` | `879ac636.png` base, then one arrow edit | `9133fb9f3c03f0345eec634be45c42a837c37795db18e1e5a23f82693b3db3fe` |
| `bed-linen-remove-duvet-cover` | `bed_linen_remove_duvet_cover.png` | `7fd4e37f.png` base, then one arrow edit | `8501b541394400b8ead3fa3e5d61fac681608372b77466565bdbae0629a3effb` |
| `bed-linen-insert-duvet-cover` | `bed_linen_insert_duvet_cover.png` | `91b8d9fd.png` base, initial arrow edit, then accepted clarity edit | `953c4ccbb0b2238a108e2116015918b5fdf930f3993ec407e8fbff6198ca89ba` |
| `bed-linen-finish-with-bedspread` | `bed_linen_finish_with_bedspread.png` | generated base, then one heading-removal edit | `524511845a0b6318741461a126d5588ac21df1a100acf5176f092e3928b9cb32` |

The retained edit sources and review contact sheet remain outside the
repository. Their hashes are recorded so the transformation ancestry can be
checked:

| Review file | Purpose | SHA-256 |
| --- | --- | --- |
| `879ac636.png` | sheet-removal base | `879ac636eec9e9c490e360327743b68b62dac7ad6c60d467ce2ed829a13ecaed` |
| `7fd4e37f.png` | duvet-removal base | `7fd4e37feebf0303c4885a1f3e65933c5e12278f08d61ec01a92ee82b956bbae` |
| `91b8d9fd.png` | duvet-insertion base | `91b8d9fdba9c0b826b83e75fa2234baf318344740d2c82e29c9356b4a616b072` |
| `12bf25ce.png` | final four-image contact sheet; not a product asset | `12bf25ce3745782ae4f95d52efe0f6bce3d53b4fac93efaa116ba634a5471d90` |

## Exact Prompts And Transformations

### Remove The Sheet From The Mattress

Base generated at 2026-07-29 01:42:57 UTC:

```text
Use case: illustration-story
Asset type: preview-only Android step image for an adult-oriented local-first visual-routines app
Primary request: show the action “Take the sheet off the mattress.”
Scene/backdrop: a minimal ordinary bedroom with only the bed area needed to understand the action
Subject: a mattress with one corner of the fitted sheet already released; two adult hands pull the old fitted sheet clearly up and away from the mattress, exposing the bare mattress underneath
Style/medium: calm high-contrast monochrome instructional illustration, black-white-gray only, clean confident outlines, restrained flat shading, functional rather than decorative
Composition/framing: landscape 4:3 composition, close three-quarter overhead view, complete mattress corner and complete hand action visible, generous margin, no crop of the functional action
Lighting/mood: neutral even light, calm and matter-of-fact
Constraints: immediately recognizable removal direction; visible instruction remains primary; no full person, hands only; no arrows, text, symbols, logos, brands, watermark, decorative room styling, dramatic perspective, aesthetic crop, color, or safety meaning
```

The accepted base was retained as `879ac636.png`. The final file was generated
from that base at 2026-07-29 04:19:42 UTC with this edit prompt:

```text
Use case: precise-object-edit
Asset type: grayscale instructional image for an Android routine app
Input image: Image 1 is the edit target and must otherwise remain unchanged.
Primary request: Add exactly one calm direction arrow showing the fitted sheet being peeled off the mattress corner upward and slightly toward the upper left. The arrow should follow the surface and fold direction of the gray sheet, with its tail near the gathered sheet just left of the right hand and its head farther up-left on the broad quiet gray fabric area.
Arrow style: match the restrained visual weight of the accepted companion images: medium graphite gray, moderately thin even stroke, gently curved, rounded cap and joins, short softly rounded arrowhead. Keep it short, approximately 16-20 percent of image width. No bright outline unless essential for legibility.
Constraints: change only by adding this single arrow; preserve the exact original illustration, composition, hands, sheet, mattress, bed, grayscale values, line work, textures, lighting, framing, resolution, and aspect ratio. Place the arrow entirely on the gray sheet. Do not overlap either hand, the sheet-mattress boundary, or the exposed mattress. No text, symbols, extra objects, color, shadow, badge, double line, or speed lines. The arrow must remain legible at a small app runner size without becoming the dominant element.
```

### Remove The Duvet From The Duvet Cover

The accepted base was generated at 2026-07-29 03:50:10 UTC:

```text
Use case: illustration-story
Asset type: preview-only bundled instructional image for an accessible Android visual-routines app
Primary request: Create a new base image showing an adult removing a full-size duvet from a duvet cover. The loose duvet, the empty cover, and the removal relationship must be recognizable without a direction arrow.
Scene/backdrop: A plain single bed with a clearly visible bare fitted sheet and mattress surface beneath the loose bedding, viewed from a moderately elevated three-quarter angle. No room decoration.
Subject and action: A large white full-size quilted duvet lies diagonally across the bed rather than aligned with the mattress. Bare mattress surface is plainly visible around and beneath it. The duvet is soft and flexible, with one complete corner and adjacent long edge lifted or draping beyond the mattress edge, clearly proving it is a loose duvet rather than a mattress topper. A medium-gray duvet cover remains around only a smaller far-end portion of the duvet. Its very wide open mouth is clearly visible wrapping around the duvet at the separation boundary, with a dark empty interior and both opening edges visible. Most of the gray cover is empty, collapsed, and bunched to one side.
Hands and action mechanics: Show two anatomically plausible adult hands and forearms only. One hand holds the exposed white duvet by its complete loose corner, lifting it slightly away from the mattress. The other hand grips only the gray cover at its broad opening and peels the empty cover backward and sideways off the duvet. The hands touch different textiles and visibly separate the two layers.
Composition/framing: 4:3 landscape. Use an oblique asymmetric viewpoint, not a symmetric foot-of-bed bed-making pose. Keep the exposed bare mattress, loose duvet corner, broad cover opening, and collapsed empty cover readable at small phone size.
Style/medium: Calm adult-oriented high-contrast monochrome instructional illustration in black, white, and gray, with clean line work and restrained soft shading.
Visual hierarchy: One action only. It must read as removing a large cover from a loose full-size duvet, not putting a cover on and not fitting a sheet around a mattress.
Constraints: No direction arrow in this base image. No text, labels, captions, numbers, logos, icons, watermark, full person, pillow, inset panel, before-and-after panel, decorative bedding, or lifestyle staging.
Avoid: The duvet lying straight and flush like a mattress topper; all mattress surface hidden by the duvet; both hands symmetrically holding one textile; a flat gray rectangle like a second blanket; a filled or taut gray cover; a compact duvet or pillow-like proportions; a pillowcase-sized opening; an insertion pose; a bed-making pose; cropped-away scale; exaggerated perspective; photorealism; cartoon styling; color.
```

The accepted base was retained as `7fd4e37f.png`. The final file was generated
from that base at 2026-07-29 03:57:36 UTC with this edit prompt:

```text
Use case: precise-object-edit
Asset type: preview-only bundled instructional image for an accessible Android visual-routines app
Input image: Image 1 is the edit target.
Primary request: Add exactly one short, calm direction arrow showing the white duvet moving out of the gray duvet cover toward the lower left.
Arrow placement: Keep the arrow entirely on the white duvet in the middle band immediately outside the gray cover opening. Start near the exposed duvet just below and left of the gray opening. Use a shallow curve down and left, ending around the central-left portion of the duvet. The arrow must remain within the upper-middle half of the exposed white duvet; do not extend it to the loose corner or into the lower third of the image. Do not cross the cover edge, hands, arms, mattress, duvet outline, or important object boundaries. It must indicate that the white duvet is moving away from and out of the gray cover.
Arrow scale and style: The complete arrow must be only about 16 to 20 percent of the image width, clearly shorter than the distance from the cover opening to the loose corner. Use one continuous gently curved medium graphite-gray line, a restrained moderately thin stroke visible at small phone size, rounded cap and joins, and a short softly rounded arrowhead. No pale outline unless absolutely necessary. No shadow, speed lines, double line, badge, sticker effect, or warning-sign appearance.
Invariants: Change only by adding this single short arrow. Preserve the original canvas size, 4:3 framing, exact hands, fingers, arms, loose white duvet, quilting, gray cover, folds, broad opening, bare mattress, bed frame, perspective, grayscale palette, line work, shading, and every existing position. Do not redraw, crop, recolor, retouch, simplify, or move anything.
Constraints: No text, labels, captions, numbers, logos, icons other than the one requested arrow, watermark, color, or additional marks.
```

### Put The Duvet Into A Clean Duvet Cover

The accepted base was generated at 2026-07-29 03:10:28 UTC:

```text
Use case: illustration-story
Asset type: preview-only bundled instructional image for an accessible Android visual-routines app
Primary request: Create a new image showing an adult putting a full-size single-bed duvet into a clean duvet cover. The intended objects and insertion direction must be unmistakable before any arrow is added, and the composition must be structurally different from a duvet being pulled out.
Scene/backdrop: A plain single bed and mattress, viewed from a moderately elevated angle. Show enough of the mattress length and both side edges to establish full-bed scale, but no surrounding room decoration.
Subject and action: A long, mostly empty medium-gray duvet cover is laid open lengthwise across the upper and middle part of the bed, extending away from the viewer toward the head of the bed. Its near open end forms a very wide, low opening across most of the bed width, with both opening corners far apart and clearly visible. In the foreground, a large white full-size duvet spans most of the bed width and shows a long straight leading edge, two corners far apart, and several broad quilted sections. Only the duvet's wide leading edge and two leading corners are just entering the gray cover opening; most of the white duvet remains visibly outside in the foreground. Two anatomically plausible adult hands, widely separated, guide the duvet's two leading corners into the opening. Show hands and forearms only.
Composition/framing: 4:3 landscape. The bed is an active scale reference. Use a slightly elevated viewpoint and enough distance to show the long empty cover ahead of the duvet. Make the cover's empty length beyond the opening visually dominant, so the action clearly reads as insertion into the cover rather than removal from it. Preserve the complete functional action at small phone size.
Style/medium: Calm adult-oriented high-contrast monochrome instructional illustration, black, white, and gray, consistent clean line work and restrained soft shading.
Visual hierarchy: One action only. The full-size duvet, broad opening, long empty cover, and insertion relationship are immediately recognizable.
Constraints: No direction arrow in this base image. No text, labels, captions, numbers, logos, icons, watermark, or full person. No decorative bedding or lifestyle staging.
Avoid: Pillow-like proportions, a compact rounded white bundle, a pillowcase-sized or square opening, a gray cover already filled or bulging, the white duvet appearing to come out toward the viewer, one hand carrying the entire object, ambiguous fabric layers, cropped-away bed scale, exaggerated perspective, photorealism, cartoon styling, color.
```

The accepted base was retained as `91b8d9fd.png`. The final file was generated
from that base at 2026-07-29 04:07:26 UTC with this edit prompt:

```text
Use case: precise-object-edit
Asset type: preview-only bundled instructional image for an accessible Android visual-routines app
Input image: Image 1 is the edit target.
Primary request: Add exactly one short, calm direction arrow showing the white duvet moving upward and away from the viewer into the open gray duvet cover.
Arrow placement: Keep the arrow entirely on the central white duvet surface immediately below the wide gray cover opening. Start in the upper-middle area of the exposed white duvet. Follow a short, very gentle curve upward toward the dark opening, ending with the arrowhead just before the duvet enters the opening. Do not cross the gray cover edge, dark opening, either hand or arm, the duvet outline, or the mattress. The arrow must clearly indicate that the white duvet is moving into the cover toward the head of the bed.
Arrow scale and style: Match the accepted calm arrow family. The complete arrow should be about 16 to 20 percent of the image width and confined to the upper-middle band of the white duvet. Use one continuous gently curved medium graphite-gray line, a restrained moderately thin stroke visible at small phone size, rounded cap and joins, and a short softly rounded arrowhead. No pale outline unless absolutely necessary. No shadow, speed lines, double line, badge, sticker effect, or warning-sign appearance.
Invariants: Change only by adding this single short arrow. Preserve the original canvas size, 4:3 framing, exact hands, fingers, arms, white duvet, quilting, gray cover, folds, wide opening, bed, mattress, perspective, grayscale palette, line work, shading, and every existing position. Do not redraw, crop, recolor, retouch, simplify, or move anything.
Constraints: No text, labels, captions, numbers, logos, icons other than the one requested arrow, watermark, color, or additional marks.
```

Real use found that this first arrow could be larger or clearer even though
complete text and routine context kept the inverse action pair viable. The
accepted clarity revision was generated from the then-current final image,
SHA-256
`9c6231a6f5ae3213bbc91afd829cddbdd6569219d88e37f0f27bb84f4ed44613`,
at 2026-08-15 01:02:42 UTC with this edit prompt:

```text
Use case: precise-object-edit
Asset type: candidate bundled instructional image for an accessible Android visual-routines app
Input image: Image 1 is the edit target.
Primary request: Replace only the existing small direction arrow with exactly one moderately larger and clearer direction arrow showing the white duvet moving upward and away from the viewer into the open gray duvet cover.
Arrow geometry: Preserve the existing central placement, upward direction, and gentle rightward curve. Make the complete arrow only about 35 to 45 percent longer than the existing arrow, roughly 180 to 210 pixels long on this 1448 x 1086 image. Extend the tail modestly downward on the central white duvet; keep the head in approximately its current place shortly before the duvet enters the dark cover opening. Keep the entire arrow on the white duvet. Do not cross the gray cover edge, dark opening, either hand or arm, duvet outline, prominent quilting seams, or mattress.
Arrow style: Use one continuous medium graphite-gray line, approximately 15 to 25 percent thicker than the existing line, with a rounded tail, rounded joins, and a compact softly rounded arrowhead proportional to the stroke. It must read at small phone runner size while remaining a calm secondary cue. No oversized or broad arrowhead, pale outline, shadow, speed lines, double line, badge, sticker effect, warning-sign appearance, or straight vertical diagram arrow.
Invariants: Change only the arrow. Remove the existing arrow completely and add the single replacement arrow. Preserve the original 1448 x 1086 canvas, 4:3 framing, exact hands, fingers, arms, white duvet, quilting, gray cover, folds, wide opening, bed, mattress, perspective, grayscale palette, line work, shading, texture, and every existing position. Do not redraw, crop, recolor, retouch, simplify, sharpen, blur, or move anything else.
Constraints: No text, labels, captions, numbers, logos, icons other than the one requested arrow, watermark, color, or additional marks.
```

The neutral-filename candidate `q7.png` retained the required 1448 x 1086,
4:3, 8-bit sRGB PNG form. A fresh-context blind first look identified the bed,
duvet, open gray cover, and upward insertion action with 96/100 object and
91/100 action confidence. Its strongest alternative was pulling the duvet
under an already positioned gray blanket; the dark opening and arrow were the
decisive cues for the intended interpretation. A following original/candidate,
phone-size, and four-image series review found the arrow visibly stronger than
the other action arrows but still calm and unobstructed. The user accepted the
revision as much clearer.

Because this was a generative edit, it also introduced subtle raster-texture
variation outside the arrow despite the invariants. The measured mean absolute
pixel difference was about 1.18 percent for the complete image and 1.16 percent
in an upper-cover crop away from the arrow. Geometry, objects, grayscale
presentation, image semantics, and the complete-text fallback remained
unchanged, and the user accepted that bounded variation. The accepted
candidate was copied into the repository byte-for-byte with SHA-256
`953c4ccbb0b2238a108e2116015918b5fdf930f3993ec407e8fbff6198ca89ba`.

### Finish The Bed With The Bedspread

The base was generated at 2026-07-29 01:46:05 UTC:

```text
Use case: illustration-story
Asset type: preview-only Android step image for an adult-oriented local-first visual-routines app
Primary request: show the finished state “Finish the bed with the bedspread.”
Scene/backdrop: a minimal ordinary bedroom with only the completed bed and a plain neutral wall
Subject: one neatly made bed with the bedspread fully laid over it, smoothed but not hotel-perfect, pillow in place, duvet contained underneath, no loose old linen and no person present
Style/medium: calm high-contrast monochrome instructional illustration, black-white-gray only, clean confident outlines, restrained flat shading, functional rather than decorative
Composition/framing: landscape 4:3 composition, simple three-quarter view showing the complete bed from head to foot, generous margin, no crop of the finished bed
Lighting/mood: neutral even light, calm ordinary completion without celebration
Constraints: immediately recognizable ordinary finished state; visible instruction remains primary; no person or hands; no arrows, text, symbols, logos, brands, watermark, decorative room styling, dramatic perspective, aesthetic crop, color, flowers, rewards, sparkles, or safety meaning
```

The generator added a heading despite the constraint. The final file was
generated from that immediately preceding image at 2026-07-29 01:47:03 UTC
with `num_last_images_to_include` set to `1` and this edit prompt:

```text
Edit the most recent generated finished-bed illustration only. Remove the entire text heading and the white title band above the illustration. Extend the same plain neutral wall background upward to fill that area naturally. Keep the bed, bedspread, pillow, monochrome instructional style, framing, lighting, proportions, and every other visual element unchanged. The final image must contain no text, letters, symbols, logos, or watermark.
```

No direction arrow was added to this finished-state image.

## Recognition And Product Review

The review was a bounded content probe, not broad user research and not
evidence that the image set will help every intended user.

- Codex generated and edited candidates, preserved provenance, and reconciled
  review input with repository product and accessibility constraints.
- Independent ChatGPT reviews used fresh contexts, neutral filenames, one
  candidate at a time, withheld intent, and a fixed first-look prompt. The
  reviewer reported objects, action and direction, confidence, strongest
  alternative interpretation, and the decisive visual detail before the
  intended meaning was revealed.
- Materially changed candidates were reviewed again in another fresh context.
  The three accepted action images passed this recognition check; the
  finished-state image was clear without an arrow.
- Fresh-context and context-rich whole-series reviews both rated the visual
  coherence 94/100 and recommended no further change.
- A separate context-rich semantic review recognized the panels, but prior
  Visual Routines context had leaked into its reasoning even when it was asked
  to behave like a first-time viewer. That result was retained only as
  contextual evidence, not counted as blind evidence.
- The user made the final product decision and accepted the revised set as one
  visual family. ChatGPT confidence scores were supporting evidence rather
  than pass thresholds or product authority.

The accepted image set later completed its bounded real-use gate in the
implemented editor and runner. All four images felt useful, neutral, and
non-distracting in context. The finished-state image was evaluated only as
part of the interface because it was not used during the physical task. This
bounded observation does not establish benefit for every intended user;
complete visible text remained primary.

## Distribution

To the extent possible under law, the Visual Routines project dedicates the
four accepted PNG files listed in this document to the public domain under
[CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/).
They may be copied, modified, and redistributed, including commercially,
without attribution.

This dedication applies only to the four listed PNG image assets. Application
source code and documentation remain under the repository's
GPL-3.0-or-later license unless a file states otherwise.
