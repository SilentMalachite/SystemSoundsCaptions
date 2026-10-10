# System Sounds Captions

マイク、PC で鳴っている音（システム音声）、音声・動画ファイルを、リアルタイムで日本語字幕にするデスクトップアプリです。
macOS と Windows で動きます。配るのは jar 1つだけです。

- 音声認識は PC の中で動きます（[Vosk](https://alphacephei.com/vosk/)）。初回だけ日本語モデル（約 48 MB）をダウンロードします。
- 字幕は、ほかのアプリより前面の半透明ウィンドウに出ます。認識中の文字は灰色、確定した文字は白です。
- 字幕ウィンドウはドラッグで動かせます。位置と文字サイズは次の起動でも使われます。

## 必要なもの

- **Java 25 以上**（例: [Adoptium Temurin](https://adoptium.net/)）
- ファイルの MP4・M4A・MOV などを読むときだけ **ffmpeg**（WAV・AIFF・MP3 は不要）
  - macOS: `brew install ffmpeg`
  - Windows: `winget install ffmpeg`

## 起動

`SystemSoundsCaptions.jar` をダブルクリックします。

ダブルクリックで起動しないときは、ターミナルから実行します。

```sh
java -jar SystemSoundsCaptions.jar
```

Java 25 より古い Java で起動すると、必要なバージョンを示すダイアログが出ます。

## 使い方

1. 「音源」で **マイク**、**システム音声**、**ファイル** のどれかを選びます。
2. マイクとシステム音声では、デバイスを選びます。ファイルでは、ウィンドウにファイルをドロップするか、点線の枠をクリックして選びます。
3. **▶ 開始** を押します。初回はモデルのダウンロードがあります。
4. 止めるときは **■ 停止** を押します。ファイルは最後まで再生すると自動で止まります。

## システム音声を字幕にする準備

Java は PC の出力音を直接録音できません。出力音を入力デバイスとして見せる「ループバック」デバイスを使います。
アプリは次の名前のデバイスを自動で選びます: BlackHole、ステレオ ミキサー（Stereo Mix）、CABLE Output（VB-CABLE）。

### macOS: BlackHole

1. `brew install blackhole-2ch` で BlackHole を入れます（または [公式サイト](https://existential.audio/blackhole/) から）。
2. 「Audio MIDI 設定」を開き、左下の **＋** → **複数出力装置を作成** を選びます。
3. 作った装置で、使っているスピーカー（またはヘッドホン）と **BlackHole 2ch** の両方にチェックを入れます。
4. システム設定 › サウンド › 出力 で、その **複数出力装置** を選びます。
5. アプリで「システム音声」を選び、デバイスが **BlackHole 2ch** になっていることを確かめます。

手順 2〜4 をしないと、音がスピーカーから聞こえなくなります。

### Windows: ステレオ ミキサー

1. 設定 › システム › サウンド › **サウンドの詳細設定** を開きます。
2. 「録音」タブで右クリックし、**無効なデバイスの表示** にチェックを入れます。
3. **ステレオ ミキサー** を右クリックし、**有効** を選びます。
4. アプリで「システム音声」を選びます。

ステレオ ミキサーが無い PC では、[VB-CABLE](https://vb-audio.com/Cable/) を入れて、出力を「CABLE Input」にします。

## うまくいかないとき

| 症状 | 対処 |
|---|---|
| 「マイクの音が届きません」と出る | macOS: システム設定 › プライバシーとセキュリティ › マイク で、Java（またはターミナル）を許可します。Windows: 設定 › プライバシーとセキュリティ › マイク で「デスクトップ アプリがマイクにアクセスできるようにする」をオンにします。 |
| 「この形式を読むには ffmpeg が必要です」 | ffmpeg を入れます（上の「必要なもの」）。 |
| モデルをダウンロードできない | ネット接続を確かめます。手動で入れるときは、[vosk-model-small-ja-0.22.zip](https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip) を下の「モデルの場所」に展開します。 |
| 字幕ウィンドウが見えない | 字幕は開始中だけ出ます。画面外に出たときは、下の設定を消すと画面下の中央に戻ります。 |

### アプリが使う場所

- モデル: `~/.systemsoundscaptions/models/vosk-model-small-ja-0.22`（Windows は `%USERPROFILE%\.systemsoundscaptions\...`）
- 音声認識ライブラリ: `~/.systemsoundscaptions/native/`（jar から初回に展開します）
- 設定（字幕の位置、文字サイズ、最後の音源）: Java の Preferences（macOS は `~/Library/Preferences/com.apple.java.util.prefs.plist`、Windows はレジストリ `HKCU\Software\JavaSoft\Prefs\captions`）

## ビルド

Java 25 以上と Maven 3.9 以上が必要です。

```sh
mvn package
```

`target/SystemSoundsCaptions.jar`（約 26 MB）ができます。macOS（arm64・x64）、Windows x64、Linux x64 用のネイティブライブラリが入っています。Linux はテストしていません。

### 構成

| パッケージ | 役割 |
|---|---|
| `captions.Launcher` | jar の入口。Java 8 向けにコンパイルし、古い Java でもエラーを表示できる |
| `captions.App`, `Session`, `Pipeline` | 起動、状態（待機中・読み込み中・認識中・エラー）、音源と認識の接続 |
| `captions.audio` | マイク・ループバック（Java Sound）、ファイル（Java Sound、mp3spi、ffmpeg）、16 kHz モノラルへの変換 |
| `captions.asr` | Vosk の呼び出し（Java FFM API）、モデルのダウンロード、無音の検出 |
| `captions.ui` | 操作ウィンドウと字幕ウィンドウ（Swing） |

設計の計画書は `docs/plan/plan.packed.html` にあります。
