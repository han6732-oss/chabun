#!/usr/bin/env bash
# music/의 장시간 원본 녹음에서 구간을 잘라 앱용 반복 음원(res/raw/ambience_*.mp3)을 만든다.
#
#  1) 한 바퀴(L초) 만들기: 클립 L+X초를 잘라 끝 X초를 처음 X초에 등전력 크로스페이드로 겹친다.
#     → 한 바퀴의 끝(클립 L 직전)과 처음(클립 L)이 이어져 반복해도 이음매가 없다.
#  2) 앞뒤에 ROLL초씩 덧붙인다: [한 바퀴의 끝 ROLL초] + [한 바퀴] + [한 바퀴의 처음 ROLL초].
#     손실 코덱은 스트림 처음과 끝에서 오차가 커서(틱 소리), 앱(AmbienceLoop)은 앞뒤 ROLL을 버리고 가운데 L초만 반복한다.
#     L은 정수 초로 둔다(앱이 파일 길이에서 L을 계산). ROLL은 AmbienceLoop.ROLL_MS와 같아야 한다.
#  3) MP3(128k CBR)로 저장한다. Vorbis는 빗방울 같은 순간음에서 짧은 패킷이 많아져 디코더 왕복이 잦고,
#     부하가 큰 에뮬레이터에서 실시간보다 느려졌다(같은 조건 MP3 2.4배, Vorbis 0.5배).
#
# 사용: tools/make_ambience.sh music app/src/main/res/raw   (ffmpeg·python 필요)
set -euo pipefail
SRC="$1"; OUT="$2"
TARGET_LUFS=-20
ROLL=0.5
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

make() { # 이름 원본 시작초 L X
  local name=$1 src=$2 a=$3 L=$4 X=$5
  local pre=2  # 리미터가 자리 잡을 앞 여유
  local ss=$((a - pre)) len=$((L + X + pre))
  local I
  I=$(ffmpeg -nostats -hide_banner -ss "$ss" -t "$len" -i "$SRC/$src" -af "atrim=start=$pre,ebur128=framelog=quiet" -f null - 2>&1 \
    | grep -E '^\s+I:' | tail -1 | awk '{print $2}')
  local gain
  gain=$(python -c "print(round($TARGET_LUFS - ($I), 2))")
  echo "$name: ${I} LUFS → ${gain} dB"
  ffmpeg -v error -y -ss "$ss" -t "$len" -i "$SRC/$src" -filter_complex "
    [0:a]aresample=44100,volume=${gain}dB,alimiter=limit=0.7:level=0:latency=1,atrim=start=$pre,asetpts=PTS-STARTPTS,asplit=3[a][b][c];
    [a]atrim=0:$X,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=$X:curve=qsin[fin];
    [b]atrim=$L:$((L + X)),asetpts=PTS-STARTPTS,afade=t=out:st=0:d=$X:curve=qsin[fout];
    [fin][fout]amix=inputs=2:normalize=0[head];
    [c]atrim=$X:$L,asetpts=PTS-STARTPTS[body];
    [head][body]concat=n=2:v=0:a=1[out]" -map "[out]" -c:a pcm_f32le "$TMP/$name.wav"
  ffmpeg -v error -y -i "$TMP/$name.wav" -filter_complex "
    [0:a]asplit=3[l1][l2][l3];
    [l1]atrim=start=$(python -c "print($L - $ROLL)"),asetpts=PTS-STARTPTS[pre];
    [l3]atrim=0:$ROLL,asetpts=PTS-STARTPTS[post];
    [pre][l2][post]concat=n=3:v=0:a=1[out]" -map "[out]" -map_metadata -1 -c:a libmp3lame -b:a 128k "$OUT/ambience_$name.mp3"
}

make cabin  비행기.mp3 3600 180 6
make space  우주.mp3   1200 180 6
make rain   오두막.mp3 1200 300 8
# 숲길 원본은 약 221초 클립을 페이드로 이어 붙인 것이라 페이드 사이(231~440초)만 쓴다.
make forest 숲길.mp3   231  203 6
