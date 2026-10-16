/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import { Composition } from "remotion";
import { DURATION, Promo } from "./Promo";

export const Root = () => (
  <>
    <Composition id="Promo" component={Promo} durationInFrames={DURATION} fps={60} width={1080} height={1920} />
    <Composition id="PromoSquare" component={Promo} durationInFrames={DURATION} fps={60} width={1080} height={1080} />
    <Composition id="PromoLandscape" component={Promo} durationInFrames={DURATION} fps={60} width={1920} height={1080} />
  </>
);
