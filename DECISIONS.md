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

## 5) Editable template repair in `ui.content`

The editable template setup in `ui.content` was updated so the sample page can render correctly in Author.

- The template structure under `/conf/assessment/settings/wcm/templates/content-page` was aligned so that both `structure/jcr:content` and `initial/jcr:content` use `core/wcm/components/page/v3/page`.
- The editable root area was aligned to `wcm/foundation/components/responsivegrid`.
- Missing template policy mappings were restored so Author can resolve the expected page structure (`root -> container -> weather`) correctly.

## 6) Authoring policy alignment

The sample page and template were aligned to a single responsive grid policy for editable areas.

- The responsive grid policy was updated to allow the `weather` component and nested layout containers.
- The template structure and sample page content were aligned to that same policy so the component renders and remains editable in Author.

## 7) Legacy config cleanup

Legacy configuration content under `/conf/global` was removed from `ui.content`.

- This change keeps the sample site configuration focused under `/conf/assessment`, avoiding reliance on shared global patterns.

## 8) Backend weather configuration

The weather service configuration was moved to OSGi configuration in `ui.config`.

- The default endpoint, API key, and timeout settings are now provided by the service PID instead of being exposed in frontend code.
- The API key is resolved from backend configuration so it can be managed securely per environment and injected as an environment secret.
- The service can still run when the provider does not require an API key.

## 9) Server-side weather delivery

The weather component was refactored to use server-side data delivery.

- Inline JavaScript and client-side API calls were removed from the HTL implementation.
- The Sling Model now reads weather data from the backend service and passes only the rendered values to the template.

## 10) Caching and resilience

The backend integration now uses server-side caching and more resilient upstream request handling.

- Weather responses are cached in memory with a configurable TTL to reduce repeated calls to the external provider.
- When a cached entry expires, the existing cached value is returned immediately while a background refresh is triggered.
- If the upstream call fails, the last cached value can still be served instead of failing the component immediately.

## 11) Dispatcher hardening

Dispatcher filters were changed to follow a deny-by-default approach.

- Only the required public content paths and supporting client library endpoints were allowlisted.
