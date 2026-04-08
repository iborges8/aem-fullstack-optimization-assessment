# Technical Decisions

## 1) Runtime and Java version

The assessment targets **AEM as a Cloud Service** as the primary runtime.

- Development and validation were performed against a current AEM Cloud SDK.
- The build was updated to use `maven.compiler.release=11` in order to preserve Java 11 compatibility, as `release` enforces API compatibility more reliably than `source`/`target`.

## 2) Package structure

The package structure was adjusted to better align with modern AEM Cloud deployment practices while keeping responsibilities between packages clearer.

- `ui.apps.structure` was kept responsible for the base `/apps/assessment` structure using `mode="merge"`.
- `ui.apps` focuses on application content under `/apps`.
- `ui.config` was kept dedicated to OSGi configuration content.
- The `core` bundle is installed from `all` instead of being embedded inside `ui.apps`.
- `all` remains the deployment container and embeds the application packages, content package, and OSGi bundle.

## 3) Validation approach

Package validation was kept strict during the build.

- Validation errors were not downgraded to warnings just to force a successful build.
- Package metadata (`group`, `name`, `version`, and `packageType`) was completed explicitly so FileVault validation and package installation behave consistently.
- The package setup was adjusted to reduce unnecessary overlap and keep local validation consistent.

## 4) Local development profile

A dedicated Maven profile was added for local development.

- `autoInstallPackage` installs the generated package directly into the local AEM author instance.
- This profile is intended only to speed up local validation and does not affect the project’s deployment structure.
