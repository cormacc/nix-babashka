{
  lib,
  stdenv,
  fetchurl,
  autoPatchelfHook,
  pname,
  version,
  repo,
  pin,
}:

let
  suffix =
    {
      "x86_64-linux" = "linux-amd64";
      "aarch64-linux" = "linux-aarch64-static";
      "x86_64-darwin" = "macos-amd64";
      "aarch64-darwin" = "macos-aarch64";
    }
    .${stdenv.hostPlatform.system};
  hasFfi = stdenv.hostPlatform.isLinux && stdenv.hostPlatform.isx86_64;
in
stdenv.mkDerivation {
  inherit pname version;

  src = fetchurl {
    url = "https://github.com/babashka/${repo}/releases/download/v${pin.version}/babashka-${pin.version}-${suffix}.tar.gz";
    hash = pin.hashes.${stdenv.hostPlatform.system};
  };

  nativeBuildInputs = lib.optionals hasFfi [ autoPatchelfHook ];
  buildInputs = lib.optionals hasFfi [ stdenv.cc.cc.lib ];

  dontBuild = true;
  sourceRoot = ".";

  installPhase = ''
    runHook preInstall

    install -Dm755 bb $out/bin/bb

    runHook postInstall
  '';

  meta = {
    description = "Native, fast starting Clojure interpreter for scripting";
    homepage = "https://babashka.org";
    license = lib.licenses.epl10;
    mainProgram = "bb";
    platforms = [
      "x86_64-linux"
      "aarch64-linux"
      "x86_64-darwin"
      "aarch64-darwin"
    ];
  };
}
