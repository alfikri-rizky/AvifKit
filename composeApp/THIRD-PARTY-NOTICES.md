# Third-party assets bundled in AVIF Studio

## Poppins

`src/commonMain/composeResources/font/poppins_*.ttf`

Copyright 2020 The Poppins Project Authors
(https://github.com/itfoundry/Poppins)

Licensed under the SIL Open Font License, Version 1.1.
Full text: <https://openfontlicense.org/open-font-license-official-text/>

Four static weights (Regular 400, Medium 500, SemiBold 600, Bold 700) are bundled rather than the
variable font, because Compose renders a variable font at its default instance — every weight would
come out identical.

---

Everything else in this module is first-party, or comes from the dependencies declared in
`build.gradle.kts` and is not redistributed as a file.
