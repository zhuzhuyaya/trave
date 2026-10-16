import { del, postForm } from './http'
import type { PhotoVO } from './types'

export const photoApi = {
  /**
   * 上传单张图片。
   *
   * 一次一张而不是一次多张:多文件接口在部分移动端浏览器上拿不到可靠的单张
   * 失败重试粒度,一张失败整批回滚的体验更糟。前端选多张时并发发几个请求,
   * 由浏览器自己排队即可。
   */
  upload: (file: File): Promise<PhotoVO> => {
    const form = new FormData()
    form.append('file', file)
    return postForm<PhotoVO>('/photos', form)
  },

  remove: (id: number) => del<void>(`/photos/${id}`),
}
