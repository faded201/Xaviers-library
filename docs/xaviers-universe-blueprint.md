# Xavier's Library Universe Blueprint

## North Star
Xavier's Library should feel like a forbidden collectible world, not a generic audiobook app.

Core pillars:
- ceremonial audiovisual experience
- 388 seeded books with long-memory canon
- holographic collector cards that influence story outcomes
- Aether Ink economy that rewards listening without feeling cheap
- intimate, uncanny narration driven by a high-quality voice engine

## Primordial Record
The first 80 chapters of the wider canon act as the hidden skeleton for the entire library.

Each future story should be able to reference:
- planted friendships
- enemy reveals
- symbols and relics
- scene seeds
- betrayals and reversals
- world rules and forbidden rooms

The app should not ask the model to reread 80 raw chapters each time.
Instead it should retrieve from structured canon data:
- `chapterSeed`
- `omen`
- `payoff`
- `influenceTags`
- relationship thread
- enemy vector
- scene signature

## Card Ladder
- Common
- Hero
- Mythical
- Legendary
- Immortal

Fusion:
- 4 Common -> 1 Hero
- 4 Hero -> 1 Mythical
- 3 Mythical -> 1 Legendary
- 2 Legendary -> 1 Immortal

Collector cards are not cosmetic. They should influence:
- protagonist archetype
- tone and pacing
- romance vs. tragedy balance
- visual atmosphere
- voice style
- reward bias
- ending flavor

## Currency
Primary currency:
- Aether Ink

Earn from:
- state awakenings
- chapter completion
- full book completion
- streak rituals
- rare omen events

Spend on:
- card reveals
- fusion rites
- special sleeves
- alternate aura themes
- event tomes

## Android Experience Rules
- preserve the dark ceremonial visual identity
- keep animations elegant and slow, never noisy
- let long-press actions reveal power-user features like sharing
- reward events should feel like omens, not casino popups
- protect weaker devices by keeping expensive generation off-device where possible

## AI Stack
Story generation:
- primary: `qwen2.5:1.5b`
- fallback for weaker setups: `gemma3:1b-it-qat`

Voice:
- primary: CosyVoice running as a backend service
- mobile fallback only when needed, not as the signature experience

Images:
- preferred: Ollama image generation or another diffusion backend off-device
- the Android app should request images from a service, not generate them locally on low-end phones

## Retrieval Contract
When generating a story scene, send:
- current tome
- current tome state
- active deck cards
- canon anchor
- relationship thread
- enemy vector
- scene signature
- reward bias or event tags

Prompt shape:
1. Keep continuity with the canon anchor.
2. Treat the active deck as modifiers, not random decorations.
3. Reward foreshadow callbacks from the planted seed chapters.
4. Keep the tone luxurious, dangerous, and emotionally sincere.

## Sharing
Every share should feel like a relic leaving the library.

Share targets:
- current tome omen
- rare card reveal
- fusion result
- story ending
- completed ritual

Link shape:
- `https://xavierslibrary.app/tome/{id}`
- `https://xavierslibrary.app/card/{id}`
- `https://xavierslibrary.app/story/{id}`

## Immediate Next Build Layers
1. Persist `LibraryEngine` state with DataStore or Room.
2. Replace placeholder tome cycling with actual chapter/story generation.
3. Add card reveal and fusion surfaces that reuse the current ceremonial design language.
4. Connect Android sharesheet content to real deep links.
5. Add backend integration for CosyVoice and the selected Ollama model.
