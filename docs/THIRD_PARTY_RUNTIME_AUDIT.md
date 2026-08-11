# Third-party runtime audit

## Scope

This record covers native files embedded in the local-validation Radiance distribution. It
separates reproducible provenance from project-owner or legal approval. Its presence does not grant
rights or declare public redistribution compliant.

## NVIDIA runtime identity

Streamline is pinned to the official v2.14.1 archive:

- URL: `https://github.com/NVIDIA-RTX/Streamline/releases/download/v2.14.1/streamline-sdk-v2.14.1.zip`
- archive SHA-256: `92C4D954631A1710DA86CA3FA8D5034F2B9503838C95FC4AE977AE149319781B`
- packaged Streamline DLL file version: `2.14.1.0`
- packaged records: `Streamline-LICENSE.txt` and `Streamline-THIRD-PARTY.md`

NGX DLSS runtimes are pinned to NVIDIA/DLSS commit
`374959484e79a640feaba44c93ac8cfb0a03f5b5`. Their package manifest records source URLs,
Git blob identities, Authenticode signer and SHA-256; the packaged file version is `310.9.1.0`.

Final distribution verification must compare every embedded file with the source-tree manifest
and record resulting JAR/DLL hashes in the development ledger. A copied license file, dynamic
loading or feature-disable switch does not by itself establish redistribution permission.

## Public-release gate

`distributedJar` remains available for local isolated validation and is marked:

```properties
scope=local-validation
publicBinaryDistributionApproved=false
```

`publicDistributionJar` depends on a deliberately failing approval gate. No repository workflow
currently publishes this installable binary or uploads it as a CI artifact. Maven publication is a
separate development component and is not the installable package.

Before enabling public binary distribution, the project owner must confirm and record:

1. that the intended distribution satisfies applicable NVIDIA SDK object-code distribution terms
   and downstream protective terms;
2. all required notices, attribution and NVIDIA trademark presentation;
3. whether the intended commercial or public release requires notification to NVIDIA, and that
   the required action has been completed;
4. that every final DLL still matches the audited version, source and hash; and
5. that the release host and CI artifact policy do not create another unauthorized distribution
   path.

These are external approval conditions. Engineering tests cannot close them, and this document is
not legal advice or a substitute for the relevant rights holder's confirmation.
