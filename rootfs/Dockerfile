# 可选：在 Docker 中执行 RootFS 交叉构建（避免污染宿主机）
#
# 使用前需在宿主机注册 binfmt_misc（qemu-user-static 通常已自动注册）：
#   docker run --rm --privileged tonistiigi/binfmt --install arm64
#
# 构建：
#   docker build -f rootfs/Dockerfile -t fishadopt-rootfs-builder .
#   docker run --rm --privileged -v "$PWD/build":/repo/build fishadopt-rootfs-builder
FROM debian:bookworm

RUN apt-get update && apt-get install -y --no-install-recommends \
    debootstrap qemu-user-static zstd git tar ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /repo

COPY rootfs/build_rootfs.sh /repo/build_rootfs.sh
COPY rootfs/start_dsh.sh /repo/start_dsh.sh
RUN chmod +x /repo/build_rootfs.sh /repo/start_dsh.sh

ENTRYPOINT ["/repo/build_rootfs.sh"]
