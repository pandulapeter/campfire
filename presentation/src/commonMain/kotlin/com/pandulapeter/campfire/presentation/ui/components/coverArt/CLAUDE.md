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

How a cover is drawn (`CoverArtImage`) and loaded (`ProvideCoverArtImageLoader`).

- Coil 3 with no network artifact and **no disk cache of its own**: bytes come from `GetCoverArtUseCase` (the offline
  copy, through `:data:source:remote`'s one HTTP client). `ProvideCoverArtImageLoader` is called once by `CampfireApp`
  and resolves the use case there.
- `CoverArtImage` reserves its room from the first frame with a placeholder, so arrival moves nothing; never on a
  missing entry's row.
- In the details bar it is part of the title (tap scrolls to top / opens About the song, no press drawn). The editor's
  bar follows typed text only after 500 ms of no typing (`rememberSettledCoverArtUrl`), since each valid half-typed URL
  would be a download.
- Follows `isCoverArtEnabled` (off: nothing fetched or drawn); performance mode keeps covers.
