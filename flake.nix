{
  description = "Prebuilt babashka binaries (release + snapshot) as a Nix flake";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      forAllSystems =
        f:
        nixpkgs.lib.genAttrs systems (
          system:
          f {
            inherit system;
            pkgs = nixpkgs.legacyPackages.${system};
          }
        );
      pins = nixpkgs.lib.importJSON ./nix/pins.json;
    in
    {
      packages = forAllSystems (
        { pkgs, ... }:
        rec {
          babashka = pkgs.callPackage ./nix/bin.nix {
            pname = "babashka";
            version = pins.release.version;
            repo = "babashka";
            pin = pins.release;
          };
          babashka-snapshot = pkgs.callPackage ./nix/bin.nix {
            pname = "babashka-snapshot";
            version = "${pins.snapshot.version}-${pins.snapshot.updated}";
            repo = "babashka-dev-builds";
            pin = pins.snapshot;
          };
          default = babashka;
        }
      );

      apps = forAllSystems (
        { system, ... }:
        {
          default = {
            type = "app";
            program = "${self.packages.${system}.default}/bin/bb";
          };
        }
      );

      checks = forAllSystems (
        { system, ... }:
        {
          babashka = self.packages.${system}.babashka;
          babashka-snapshot = self.packages.${system}.babashka-snapshot;
        }
      );

      formatter = forAllSystems ({ pkgs, ... }: pkgs.nixfmt-rfc-style);

      overlays.default = final: prev: {
        babashka = final.callPackage ./nix/bin.nix {
          pname = "babashka";
          version = pins.release.version;
          repo = "babashka";
          pin = pins.release;
        };
        babashka-snapshot = final.callPackage ./nix/bin.nix {
          pname = "babashka-snapshot";
          version = "${pins.snapshot.version}-${pins.snapshot.updated}";
          repo = "babashka-dev-builds";
          pin = pins.snapshot;
        };
      };
    };
}
