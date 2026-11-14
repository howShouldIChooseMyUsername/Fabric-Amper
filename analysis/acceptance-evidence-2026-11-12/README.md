# Fabric Amper selected runtime evidence (preserved before `clean`)

This folder is a **snapshot of selected actual logs and proof markers**, copied from the producer project's `build/fabric-amper-runtime-consumers` before the final clean-build check. The authoritative cumulative Korean assessment remains `../../QA_REPORT.md` (sections 18 and 19); no PASS should be inferred from a copied file alone.

| Folder/file | What the evidence demonstrates | Limit |
| --- | --- | --- |
| `G10-positive-logs`, `G10-positive-proof` | External production JAR and nested Commons Collections4 4.5.0 actually discovered by Loader; library invocation, host and tick nonce markers | Maven `include` only; no general guarantee for every scope |
| `G10-negative-proof` | Separately isolated host without the external JAR confirmed absence of the nested library | Negative control only |
| `G15-initial-PASS-proof` | Earlier isolated G15 actual Loader `main`, `client` and game `tick` nonce markers | Later fresh G15 run failed during native SDL initialization |
| `G17-native-main-client-proof`, `G17-native-failure-logs` | Class Tweaker interface default method actually called from Loader `main`, and client initializer reached; native process then exited 0xC0000409 before game tick | Does **not** establish successful G17 client tick; root cause unknown |
| `G15-control-native-failure-logs` | Later simple G15 control also exited with native 0xC0000409 after SDL initialization | No proof that Class Tweaker caused the native exit |
| `G27-1PASS.xml` | Selected G27 TestKit 1 PASS, 0 FAIL, 0 SKIP: bad global-only mixin rejected; client-only dependency blocked in common source; good metadata/build passed | Physical dedicated server NOT RUN; EULA not approved |
| `G19-real-external-Loom-1PASS.xml`, `G19-real-external-Loom.txt` | Published-plugin settings-only consumer: actual external Loom 1.18.4 CodeSource/instance proof then FA010 conflict, independent bundled 1.17.21 control `help` PASS; TestKit 1 PASS, 0 FAIL, 0 SKIP | Real external KGP conflicts are a separate unverified subcase |

The other cumulative result sets, including G17 custom-only positive/negative compile acceptance, are recorded in QA sections and source TestKit fixtures rather than being recreated artificially after the selected XML outputs were replaced by subsequent TestKit runs. The historical 148 PASS / 17 SKIP `clean build` and earlier output SHA belong to their original point in time; they are not the hash of any future clean-build output.
