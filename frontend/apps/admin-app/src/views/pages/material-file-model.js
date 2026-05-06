export const MAX_COURSE_MATERIAL_FILE_SIZE_MB = 200
export const MAX_COURSE_MATERIAL_FILE_SIZE_BYTES = MAX_COURSE_MATERIAL_FILE_SIZE_MB * 1024 * 1024

export function validateCourseMaterialFile(file) {
  if (!file) {
    return '请选择 PDF 资料文件'
  }

  const fileName = String(file.name ?? '')
  if (file.type && file.type !== 'application/pdf') {
    return '课程资料仅支持 PDF 文件'
  }

  if (!fileName.toLowerCase().endsWith('.pdf')) {
    return '文件扩展名必须是 .pdf'
  }

  if (Number(file.size ?? 0) > MAX_COURSE_MATERIAL_FILE_SIZE_BYTES) {
    return `PDF 文件不能超过 ${MAX_COURSE_MATERIAL_FILE_SIZE_MB}MB`
  }

  return ''
}
