# Fonts

The design system specifies Unbounded for display type and Figtree for UI type
(design/tokens.json, `font.*`). Both are published by Google Fonts under the
SIL Open Font License, which permits redistribution inside an application.

Upstream ships only variable fonts. iOS does not select a variable weight
reliably from SwiftUI, so these are static instances cut at exactly the weights
the design system uses, with the name table updated so each weight registers as
its own family:

| File | Weight | Used for |
| --- | --- | --- |
| Unbounded-ExtraBold.ttf | 800 | display type at weight 800 |
| Unbounded-Black.ttf | 900 | logo, year, screen titles, club names, score |
| Figtree-Medium.ttf | 500 | secondary copy |
| Figtree-SemiBold.ttf | 600 | answer buttons |
| Figtree-Bold.ttf | 700 | body copy (`type.body`) |
| Figtree-ExtraBold.ttf | 800 | emphasis |
| Figtree-Black.ttf | 900 | labels and chips |

Regenerate with `./scripts/gen-fonts.sh`.

Licenses: `OFL-Unbounded.txt`, `OFL-Figtree.txt`. Keep them alongside the fonts;
the OFL requires the license to travel with the font.
