<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/components/coverArt

- Coil 3 with no network artifact and **no disk cache of its own**: bytes come from `GetCoverArtUseCase` (the offline
  copy, through `:data:source:remote`'s one HTTP client).
- `CoverArtImage` reserves its room from the first frame, so arrival moves nothing; never on a missing entry's row.
- In the details bar it is part of the title (no press drawn). The editor's bar follows typed text only after 500 ms of
  no typing, since each valid half-typed URL would be a download.
- Off with `isCoverArtEnabled` (nothing fetched or drawn); performance mode keeps covers.
