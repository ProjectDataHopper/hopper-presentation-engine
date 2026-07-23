# Publishing `org.hopper` artifacts to Nexus

Target repository (hosted Maven 2):

| Field | Value |
|-------|--------|
| Nexus UI | https://repository.data-hopper.com/ |
| Repository name | **hopper** |
| Deploy URL | **https://repository.data-hopper.com/repository/hopper/** |
| Maven `server` id | **`hopper`** (must match POMs’ `distributionManagement`) |

The ecosystem modules (`hopper-presentation-core`, `hopper-presentation-rest`, `hopper-hop-plugins`, `hop-hopper-plugins`) use this URL for both **snapshots** and **releases**. In Nexus, the `hopper` hosted repo version policy should be **Mixed** (or equivalent) so both `*-SNAPSHOT` and release versions are accepted. If you later split into `hopper-snapshots` / `hopper-releases`, only the URLs in each POM (or a parent POM) need to change.

---

## Where credentials live (never in git)

### Local / developer machine: `~/.m2/settings.xml`

Credentials belong in your **user Maven settings**, not in any project POM or repository:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">

  <servers>
    <!-- id MUST equal distributionManagement repository id in the POMs -->
    <server>
      <id>hopper</id>
      <username>YOUR_NEXUS_USERNAME</username>
      <password>YOUR_NEXUS_PASSWORD_OR_TOKEN</password>
    </server>
  </servers>

  <!-- Optional: resolve org.hopper deps from the same repo -->
  <profiles>
    <profile>
      <id>hopper-nexus</id>
      <repositories>
        <repository>
          <id>hopper</id>
          <url>https://repository.data-hopper.com/repository/hopper/</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>hopper</id>
          <url>https://repository.data-hopper.com/repository/hopper/</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>hopper-nexus</activeProfile>
  </activeProfiles>
</settings>
```

**Security tips:**

1. Prefer a Nexus **user token** over your login password.
2. Optionally encrypt the password with Maven’s master password:
   - `mvn --encrypt-master-password` → store in `~/.m2/settings-security.xml`
   - `mvn --encrypt-password` → put the `{...}` ciphertext in `<password>`
3. File permissions: `chmod 600 ~/.m2/settings.xml`
4. **Do not commit** `settings.xml` or tokens to GitHub.

A copy-paste template without secrets is also in [`maven-settings-hopper.example.xml`](maven-settings-hopper.example.xml).

### CI (GitHub Actions)

Store secrets in the repo or org:

- `HOPPER_NEXUS_USERNAME`
- `HOPPER_NEXUS_PASSWORD`

The workflow writes a temporary `settings.xml` (or uses `actions/setup-java` `server-id: hopper`) and runs `mvn deploy`. See [`.github/workflows/deploy-snapshot.yml`](../.github/workflows/deploy-snapshot.yml).

---

## Deploy snapshots (manual)

From each module (after hopper-presentation-core is published first if others depend on it):

```bash
# 1) hopper-presentation-core (version must end with -SNAPSHOT)
cd ~/git/mattcasters/hopper-presentation-core
mvn clean deploy

# 2) hopper-hop-plugins
cd ~/git/mattcasters/hopper-hop-plugins
mvn clean deploy -Dmaven.test.skip=true   # or full test if preferred

# 3) hop-hopper-plugins
cd ~/git/mattcasters/hop-hopper-plugins
mvn clean deploy -Dmaven.test.skip=true

# 4) hopper-presentation-rest (WAR)
cd ~/git/mattcasters/hopper-presentation-rest
mvn clean deploy
```

Or use the helper script from hopper-presentation-core:

```bash
cd ~/git/mattcasters/hopper-presentation-core
./scripts/deploy-snapshots.sh
```

Verify in Nexus: **Browse → hopper → org/hopper/...**

---

## Deploy releases (manual)

1. Set a **non-SNAPSHOT** version in the POM(s), e.g. `1.0.0`.
2. Tag the git commit if you use tags: `git tag v1.0.0 && git push --tags`
3. `mvn clean deploy` (same order: engine → hop plugins → rest).
4. Bump POMs back to the next `*-SNAPSHOT` for continued development.

Optional: Maven Release Plugin (`mvn release:prepare release:perform`) automates version bumps and tagging; it still uses the same `distributionManagement` + `settings.xml` server `hopper`.

---

## Automatic snapshot deploy on push

If the GitHub Action is enabled, every push to `master` that keeps a `-SNAPSHOT` version can publish to Nexus using repository secrets. Releases should stay intentional (tag + release workflow or manual deploy).

---

## Consumer projects

To **depend** on published artifacts, either:

- activate a profile that points at `https://repository.data-hopper.com/repository/hopper/`, or  
- add that repository to the consumer POM, or  
- if `hopper` is later added to a Nexus **group** (e.g. `maven-public`), consumers can use the group URL only.

Example dependency:

```xml
<dependency>
  <groupId>org.hopper</groupId>
  <artifactId>hopper-presentation-core</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `401 Unauthorized` / `403` | Wrong username/password, or server `<id>` ≠ `hopper` |
| `400` / version policy error on SNAPSHOT | Set hosted repo policy to **Mixed**, or use a dedicated snapshots repo URL |
| Artifact not found by others | They need a repository entry for the hopper URL (or a group that includes it) |
| CI fails only on deploy | Check GitHub secrets and that the workflow passes `-s settings.xml` / `server-id: hopper` |
