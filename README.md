# nix-babashka

A Nix flake that packages the prebuilt upstream [babashka](https://babashka.org)
binaries.

Two packages:
- `babashka` — the latest [`babashka/babashka`](https://github.com/babashka/babashka)
  GitHub release.
- `babashka-snapshot` — the latest master build from
  [`babashka/babashka-dev-builds`](https://github.com/babashka/babashka-dev-builds).

Both packages are pinned in `nix/pins.json` and kept current automatically:
`.github/workflows/bump.yml` runs every 6 hours (and on `workflow_dispatch`)
to check upstream for a new release tag or changed snapshot hashes, gates any
change on a Linux `nix build` of both packages, and commits the pin update to
`main`. No manual hash editing.

## Usage

Run the release binary directly:

```console
$ nix run github:cormacc/nix-babashka -- --version
babashka v1.13.223
```

Build either package:

```console
$ nix build '.#babashka'          # release, packages.default
$ nix build '.#babashka-snapshot' # latest master build
$ result/bin/bb --version
```

### As a flake input

```nix
{
  inputs.nix-babashka.url = "github:cormacc/nix-babashka";

  outputs = { self, nixpkgs, nix-babashka }: {
    # ...
    nixosConfigurations.example = nixpkgs.lib.nixosSystem {
      modules = [
        {
          nixpkgs.overlays = [ nix-babashka.overlays.default ];
          environment.systemPackages = [ pkgs.babashka ]; # or pkgs.babashka-snapshot
        }
      ];
    };
  };
}
```

`overlays.default` adds `babashka` and `babashka-snapshot` to `pkgs`, in place
of nixpkgs' own (older, libffi-less) `babashka`.

## Release vs snapshot

`babashka` tracks the latest tagged GitHub release. `babashka-snapshot`
tracks the latest CI build off `master`, whose version string contains
`SNAPSHOT`; use it to pick up fixes ahead of the next release. Both pins
update automatically every 6 hours — trigger `bump.yml` manually
(`workflow_dispatch`) to force an immediate check.

## Platform / libffi caveat

Upstream ships a `babashka.ffi`-capable (libffi-linked) binary only for
`x86_64-linux` and both macOS platforms. `aarch64-linux` has only the static
musl build, which upstream builds without libffi, so `babashka.ffi` is
unavailable there.

## LLM-use disclosure

This flake was largely written by an LLM -- because life's too short to write nix.
