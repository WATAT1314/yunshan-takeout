import request from '@/utils/request'

// 经营诊断
export const diagnose = () =>
  request({
    'url': `/agent/diagnose`,
    'method': 'get'
  } as any)

// 关键词问答
export const ask = (data: { question: string }) =>
  request({
    'url': `/agent/ask`,
    'method': 'post',
    data
  } as any)
