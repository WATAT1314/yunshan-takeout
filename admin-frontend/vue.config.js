const path = require('path')
const name = '云膳到家管理端'
const IS_PROD = ['production', 'development'].includes(process.env.NODE_ENV)

module.exports = {
  'publicPath': process.env.NODE_ENV === 'production' ? './' : '/',
  'lintOnSave': false,
  'pwa': {
    'name': name
  },
  'pluginOptions': {
    'style-resources-loader': {
      'preProcessor': 'scss',
      'patterns': [
        path.resolve(__dirname, 'src/styles/_variables.scss'),
        path.resolve(__dirname, 'src/styles/_mixins.scss')
      ]
    }
  },
  // 开启代理
  devServer: {
    port: 8888,
    open: true,
    disableHostCheck: true,
    hot: true, // 自动保存
    overlay: {
      warnings: false,
      errors: true
    },
    proxy: {
      '/api': {
        target: process.env.VUE_APP_URL,
        ws: false,
        secure: false,
        changeOrigin: true,
        pathRewrite: {
          '^/api': ''
        }
      }
    }
  },
  chainWebpack: (config) => {
    config.resolve.symlinks(true) // 修复热更新失效
    // TS 3.6.2 无法解析 vue-router 3.6 / vue 2.7 的新版类型语法（export type {}），
    // 类型检查交给 IDE；构建时移除 fork-ts-checker 插件避免误报阻断。
    config.plugins.delete('fork-ts-checker')
    // 关闭构建缓存（缓存目录在 junction 指向的共享 node_modules 里，避免写冲突）
    config.cache(false)
  },
  configureWebpack: {
    devtool: 'source-map'
  },

  css: {
    // 是否使用css分离插件 ExtractTextPlugin
    extract: IS_PROD,
    // 开启 CSS source maps?
    sourceMap: false,
    // css预设器配置项
    loaderOptions: {
    },
    // 启用 CSS modules for all css / pre-processor files.
    modules: false
  }
}
