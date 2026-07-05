# Reference Mod Inventory - 2026-07-04

## Scope

This inventory was taken from the local Prism instance at:

`/home/masuary/.local/share/PrismLauncher/instances/Wold's Vaults - Vault Hunters Expansion/minecraft/mods`

It is a client-pack reference, not proof of the production server's deployed JAR set. Production sign-off still requires an inventory of the actual server directory.

## Deployment blockers found

- Two active JARs declare `woldsvaults`: versions `0.30.6` and `0.31.1`. Only the intended production version may remain active.
- Three active JARs declare `masutab`: versions `2.2.0`, `2.2.1`, and `2.2.2`. Only the intended production version may remain active.
- `chestmonster`, `companion_work_station`, `creeperpower`, and `storagenetwork` are not installed in this reference pack. Their resources remain optional and are skipped when their mod ID is absent.

## Direct and custom integration evidence

| Mod ID | Reference JAR | SHA-256 | Coverage |
|---|---|---|---|
| `the_vault` | `the_vault-1.18.2-3.21.5.6573.jar` | `58672f06c4b3564a3daf4020a1492b05245f0ca13604cfc3519caf3327f81ea3` | Permissions, Wardrobe, altar, pedestal, portals |
| `buildscape` | `buildscape-3.0.2-VH.jar` | `cf1ee2302896921490b9e1fa62014f187c448207d43b71c561552482a5357304` | Pillar theft prevention |
| `effortlessbuilding` | `effortlessbuilding-1.18-2.40.jar` | `aaeb065c9f7cdc81ffb941b9e4c67909ad7d7969b95d1ec207081fa43c689b34` | Single and batch placement/break protection |
| `pneumaticcraft` | `pneumaticcraft-repressurized-1.18.2-3.6.4-45.jar` | `d19a51835892ce2f27108f4aaa8da95588d2c4631ddae169e05df2332949b0b8` | Drone owner checks, pickup, damage, Amadron exception |
| `ars_nouveau` | `ars_nouveau-1.18.2-2.9.0.jar` | `35aa0b3ecb218e3355d99d7302bf3f633b982f9072b39abcdb0fc176ebeecdff` | Block interaction and Warp Portal protection |
| `masugui` | `masugui-1.0.0.jar` | `8ebac4c759440b40fc12494519a6b08ab927174aebd7213fef6ad65e48b85423` | Optional enhanced GUI, no MasuGUI source changes |
| `vending_companions` | `companion_locker-3.21.0.jar` | `da5cb711836f60b7f566d8ed74aeaaf0ccb2f1d2e11510f803874117e05c2ab5` | `companion_vending_machine` permission group |
| `woldsvaults` | `wolds-vaults-official-mod-0.31.1.jar` | `16849f5b9e18f740253dd890db21e69575efa64670ebc5dc33cd05009c4584d1` | Exact interactive block registry audit and Vault permission groups |
| `mobprocessor` | `mobprocessor-1.0.jar` | `5d4fd06cd08a69737d1e4583f335d5c8b7dcb78eede1b0562ecb3e6da9a61cb3` | Dedicated `mob_processor` permission |
| `customcompanions` | `customcompanions-1.0.jar` | `f9492e0a2fa98bd7473c1a6643a9b75b7f1c50e6ff8705a48d2fa167172c108b` | No registered blocks or entities requiring island rules |
| `masucompanion` | `masucompanion-1.4.3.jar` | `6a120d4952bb1e0f347e941ad172d1d711fc9a4ed3badecb4849f09cef291d50` | No registered blocks or items requiring island rules |
| `masuplots` | `masuplots-0.7.0.jar` | `454885070e2ac822ef9b06f478cc6bcf79af1ea42285e819f7be62670b8cf00c` | Separate plot protection system; no registered blocks |

## Permission resource gates

| Mod ID | Reference JAR | SHA-256 |
|---|---|---|
| `ae2` | `appliedenergistics2-forge-11.7.6.jar` | `86f06ffdd7b73848cbb82ff23cf6bba6b2949e0562ae8a5f68bf0eed86eba8d2` |
| `ae2things` | `AE2-Things-1.0.7.jar` | `9dd028d7b2ff06825b0b645f4746a4098b6bafa689f2af0a9be31538d22c9fd3` |
| `appbot` | `Applied-Botanics-1.0.3.jar` | `7493b2cc5a8d4ec8c9f2066bed3da5ff3b3011a227f785978d9b8db5d1aa1164` |
| `appmek` | `Applied-Mekanistics-1.2.2.jar` | `e3b730fcf65f2e3120567b5862efb02e7c1f630933b25e0cd331817a52b595b0` |
| `ars_nouveau` | `ars_nouveau-1.18.2-2.9.0.jar` | `35aa0b3ecb218e3355d99d7302bf3f633b982f9072b39abcdb0fc176ebeecdff` |
| `blockcarpentry` | `blockcarpentry-1.18-0.5.1.jar` | `5bf4c5602052db3e74ff2511f5ef3f48e86e69c1e5e780d18373ae7f677ae925` |
| `botania` | `Botania-1.18.2-435.jar` | `1300bc74d0cc1fad40261b0a888fc3c8b88594c43b9063b5f6a6b7456518ed1f` |
| `cfm` | `cfm-7.0.0-pre35-1.18.2.jar` | `48d55a12e01d5d2de970b4be4f3eea188202f8a95058c8bb47b50f12d61661b2` |
| `cloudstorage` | `cloudstorage-1.1.0-1.18.2.jar` | `af9e853fe540ffc79a47ebb454c22043c0842506799c48f19b2d7db42810413b` |
| `colossalchests` | `ColossalChests-1.18.2-1.9.0.jar` | `b8d80fde78ec8837ef4ef7f0207547b497c79c70a81d6f1d2ae4e98a67885e90` |
| `cookingforblockheads` | `cookingforblockheads-forge-1.18.2-12.2.0.jar` | `4201ea207da01c2d4593f6df0f0ff7a4c538fc5e92fd0767ff37bfde6ee2e231` |
| `create` | `create-1.18.2-0.5.1.i.jar` | `5311dbc98d734d7cf0f38f3a9a149234136b912228cdf01b746fbe32d41d0fe2` |
| `createaddition` | `createaddition-1.18.2-1.0.0.jar` | `359d3811abfcc775822b758a575649dee3c14660dab03f16ce1235687d2b330f` |
| `davespotioneering` | `davespotioneering-1.18.2-3.jar` | `c1995ff923c09348d4bc06351e09fbbad327642aa5869da14cc251769917d74f` |
| `easy_piglins` | `easy_piglins-1.18.2-1.0.4.jar` | `26b25f13e434ece60e319066e426291dc4ca152333da76f64028bd02df56d2b0` |
| `easy_villagers` | `easy_villagers-1.18.2-1.0.11.jar` | `544746bf3cc763f8321f985ef450d3104357828ef2c275e53332b9f229cf43ea` |
| `elevatorid` | `elevatorid-1.18.2-1.8.4.jar` | `1798ba3fd9082e5436b3d429f5c81393a9aed2042a5e1ab8ad0c6895d283ac8f` |
| `everycomp` | `everycomp-1.18.2-1.6.7.jar` | `9b12145ac6985acd919d926ad7d3f5b83c635972e568f61cda8efdbeebb3f85b` |
| `extrastorage` | `ExtraStorage-1.18.2-2.2.1.jar` | `9335fddc1fe46b3d59808c89bb72f4b518c06e7931d7cdf7e48548afd86b1b4b` |
| `fluxnetworks` | `FluxNetworks-1.18.2-7.0.9.15.jar` | `07be413ba4304f51663a58f20d84e8e1b834b6a1424b8dd3921b0c688f6d3323` |
| `framedcompactdrawers` | `framedcompactdrawers-1.18-4.1.0.jar` | `701a9a0cb381d57c9e440f3698ac8b6adaf5fdb787342bb8cf7e9e50fd885d43` |
| `functionalstorage` | `functionalstorage-1.18.2-1.1.3.jar` | `fbfaa1a66d9bc43cb3670d5a9590b000ccb6d02904f80f0cff83b0438f76f453` |
| `immersiveengineering` | `ImmersiveEngineering-1.18.2-8.4.0-161.jar` | `6e9b1ad1fc29d10863465bb48c65ad4d5df74042a5b0ee50c6289b687cd38a74` |
| `industrialforegoing` | `industrial-foregoing-1.18.2-3.3.1.6-10.jar` | `32e051d753ce3dbdca16ecb2f5cc1b882b62ff37c54d60c112df6180982df8f8` |
| `integrateddynamics` | `IntegratedDynamics-1.18.2-1.17.4.jar` | `555fe72885e107b3ae4ec8d838f7fd0190d8ff1660396b449fd64728c7dabb5c` |
| `ironfurnaces` | `ironfurnaces-1.18.2-3.3.3.jar` | `ef585a7a3f6d32541335d06eb3e18410603d815152425ff29174733eae35146d` |
| `mcwfences` | `mcw-fences-1.1.1-mc1.18.2forge.jar` | `b8c5e95a6e5fca8e5423a45737b1ac94d41a1797e7397137b12014cdfe0c5723` |
| `megacells` | `MEGACells-1.4.2-1.18.2.jar` | `bb85c40e98ce27a716138ec09bbdbf46d076943bf8905dd37bf70bf13763c63f` |
| `mekanism` | `Mekanism-1.18.2-10.2.5.465.jar` | `ae5f3818940aa99cf3bf51786a9b66284327d14120924bd99f864e6b5457e523` |
| `mobprocessor` | `mobprocessor-1.0.jar` | `5d4fd06cd08a69737d1e4583f335d5c8b7dcb78eede1b0562ecb3e6da9a61cb3` |
| `moreburners` | `moreburners-1.18.2-0.3.1.jar` | `2e47bd526293353819d475027e61657fd7ef8e1102c3a1bee60dd5a31853f230` |
| `mysticalagriculture` | `MysticalAgriculture-1.18.2-5.1.5.jar` | `5e6b52a44675168a1b8b4c8ec887342269062493e89a4dbb15980367103307b4` |
| `occultism` | `occultism-1.18.2-1.84.0.jar` | `f0d75328eaa6f3854fa264dce88b0a82606526bb0da5b2ef358cf8122a027f54` |
| `pneumaticcraft` | `pneumaticcraft-repressurized-1.18.2-3.6.4-45.jar` | `d19a51835892ce2f27108f4aaa8da95588d2c4631ddae169e05df2332949b0b8` |
| `quark` | `Quark-3.2-358.jar` | `046c5cbc31259eb175d010bac36aa82cf6678254caca79cd65022f5b28e031cd` |
| `railways` | `Steam_Rails-1.4.8+forge-mc1.18.2-build.23.jar` | `84185cb19575d9d436e1b78d98e74f0dbc48b9bcf6653ac392b8dc79798bcc24` |
| `refinedstorage` | `refinedstorage-1.10.6.jar` | `92157fdaa3e0f4541ff020f1721408167e453cbb4ff50c966ceeece970867512` |
| `rftoolsbase` | `rftoolsbase-1.18-3.0.12.jar` | `bf3bc751aa624a0ac8a7d0e850fb95e2a59fa53a7f9fab327da2ddd2e61f2faf` |
| `rftoolspower` | `rftoolspower-1.18-4.0.9.jar` | `4c0dc132c0e4bcd9ec1454211d42c87eaf74216d8c36fbb52fd5b5e058bbb82a` |
| `rftoolsstorage` | `rftoolsstorage-1.18-3.0.12.jar` | `89987e49d2af49c795a9296f6b9e47b9742c3f0e2873c2be79988d289353e70e` |
| `rftoolsutility` | `rftoolsutility-1.18-4.0.24.jar` | `61691b95ba233da242bd92fe64e8f7e135e8ececac0818f8ab2ed0186d970c2d` |
| `sfm` | `SuperFactoryManager-1.18.2-4.1.1.jar` | `0afab3bd827ab28aa3e25de300d5c99cc8aaf094443230a3e55c9b493a8dd981` |
| `sophisticatedbackpacks` | `sophisticatedbackpacks-1.18.2-3.20.3.1063.jar` | `5b5b0c67f301b385e074ab6900930f4f6c3c3e0355c8611cd378bf27c023948d` |
| `sophisticatedstorage` | `sophisticatedstorage-1.18.2-0.9.8.915.jar` | `3965b3af65ebd0244ead0bdab23cd3224e41a616b73049d2294053504a452060` |
| `storagedrawers` | `StorageDrawers-1.18.2-10.2.1.jar` | `5a7bcfe6a289d3857ca4fd2609f7d0ee3e2cca9228dad81d138d0675f80c5c99` |
| `supplementaries` | `supplementaries-1.18.2-1.5.18.jar` | `2ab0363daed627b9c8df5908cf89f9b61000ece499a6b6016f63206da17df384` |
| `the_vault` | `the_vault-1.18.2-3.21.5.6573.jar` | `58672f06c4b3564a3daf4020a1492b05245f0ca13604cfc3519caf3327f81ea3` |
| `thermal` | `thermal_foundation-1.18.2-9.2.2.58.jar` | `2ebb58af57b0e64482b7809cf7f4c19c5725dc671317893302694660110def8f` |
| `toms_storage` | `toms_storage-1.18.2-1.4.4.jar` | `631f1d3136f79453ca7e7bd1919992d3e7afa9a06de86946c4c7d1524aa76035` |
| `vending_companions` | `companion_locker-3.21.0.jar` | `da5cb711836f60b7f566d8ed74aeaaf0ccb2f1d2e11510f803874117e05c2ab5` |
| `waystones` | `waystones-forge-1.18.2-10.2.2.jar` | `26d0e83ed96b6146ae3680132cb2ef77eeb7f70c0abe4172a66926dd01429fd4` |
| `woldsvaults` | `wolds-vaults-official-mod-0.31.1.jar` | `16849f5b9e18f740253dd890db21e69575efa64670ebc5dc33cd05009c4584d1` |

## Registry corrections made from this inventory

- Corrected `woldsvaults:vault_salager` to the registered `woldsvaults:vault_salvager` and normalize that typo when importing legacy configuration.
- Added the missing `mobprocessor:mob_processor` permission.
- Corrected the Vault companion placement trigger from `place_blocks` to `onPlaceBlock`.
- Replaced obsolete Create block identifiers with the installed 0.5.1 identifiers.
- Replaced obsolete Mekanism factory/tank identifiers and added the `mekanismgenerators` namespace to the granular machine permission.
- Removed the obsolete `the_vault:soul_harvester` block identifier.
