package com.perfree.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.perfree.common.Pager;
import com.perfree.config.DynamicDataSource;
import com.perfree.directive.DirectivePage;
import com.perfree.mapper.ArticleMapper;
import com.perfree.model.Archive;
import com.perfree.model.Article;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class ArticleService{
    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CategoryService categoryService;

    /**
     * 添加文章
     * @param article article
     * @return int
     */
    public int add(Article article) {
        article.setViewCount(0L);
        article.setCommentCount(0L);
        article.setCreateTime(new Date());
        int result = articleMapper.add(article);
        if (article.getArticleTags().size() > 0) {
            article.getArticleTags().forEach(r -> {
                r.setArticleId(article.getId());
            });
            // 添加标签关联
            articleMapper.addArticleTag(article.getArticleTags());
        }
        if (article.getCategoryId() != null) {
            categoryService.addCount(article.getCategoryId());
        }
        return result;
    }

    /**
     * 文章管理列表数据
     * @param pager pager
     * @return  Pager<Article>
     */
    @Transactional(readOnly = true)
    public Pager<Article> list(Pager<Article> pager) {
        PageHelper.startPage(pager.getPageIndex(), pager.getPageSize());
        List<Article> articles = articleMapper.getList(pager.getForm());
        PageInfo<Article> pageInfo = new PageInfo<>(articles);
        pager.setTotal(pageInfo.getTotal());
        pager.setData(pageInfo.getList());
        pager.setCode(Pager.SUCCESS_CODE);
        return pager;
    }

    /**
     * 更改置顶状态
     * @param article article
     * @return int
     */
    public int changeTopStatus(Article article) {
        article.setUpdateTime(new Date());
        return articleMapper.changeTopStatus(article);
    }

    /**
     * 更改是否可以评论
     * @param article article
     * @return int
     */
    public int changeCommentStatus(Article article) {
        article.setUpdateTime(new Date());
        return articleMapper.changeCommentStatus(article);
    }

    /**
     * 更改文章状态
     * @param article article
     * @return int
     */
    public int changeStatus(Article article) {
        article.setUpdateTime(new Date());
        return articleMapper.changeStatus(article);
    }

    /**
     * 删除文章
     * @param idArr idArr
     * @return int
     */
    public int del(String[] idArr) {
        Arrays.asList(idArr).forEach(r -> {
            Article article = articleMapper.getById(r);
            if (article.getCategoryId() != null) {
                categoryService.subCount(article.getCategoryId());
            }
            articleMapper.deleteTagByArticleId(r);
        });
        return articleMapper.del(idArr);
    }

    /**
     * 根据id获取文章信息
     * @param id id
     * @return Article
     */
    @Transactional(readOnly = true)
    public Article getById(String id) {
        return articleMapper.getById(id);
    }

    /**
     * 更新文章
     * @param article article
     * @return int
     */
    public int update(Article article) {
        article.setUpdateTime(new Date());
        // 先删除标签关联
        articleMapper.deleteTagByArticleId(article.getId().toString());
        if (article.getArticleTags().size() > 0) {
            article.getArticleTags().forEach(r -> {
                r.setArticleId(article.getId());
            });
            // 添加标签关联
            articleMapper.addArticleTag(article.getArticleTags());
        }
        // 先将原分类数量减一
        Article oldArticle = articleMapper.getById(article.getId().toString());
        if (oldArticle.getCategoryId() != null){
            categoryService.subCount(oldArticle.getCategoryId());
        }
        // 将新分类数量加一
        if (article.getCategoryId() != null){
            categoryService.addCount(article.getCategoryId());
        }
        return articleMapper.update(article);
    }

    /**
     * 获取文章数量
     * @return Long
     */
    public Long getArticleCount() {
        return articleMapper.getArticleCount();
    }

    /**
     * 获取最新的22条文章(后台首页)
     * @return List<Article
     */
    public List<Article> getArticleListByDashboard() {
        return articleMapper.getArticleListByDashboard();
    }

    /**
     * 获取热门文章
     * @return List<Article>
     */
    public List<Article> getHotArticle(int count,int type) {
        return articleMapper.getHotArticle(count,type);
    }

    public List<Article> getLatestArticle(int count) {
        return articleMapper.getLatestArticle(count);
    }

    /**
     * 前台文章分页
     * @param directivePage directivePage
     * @return List<Article>
     */
    public DirectivePage<HashMap<String, String>> frontArticlesPage(DirectivePage<HashMap<String, String>> directivePage) {
        PageHelper.startPage(directivePage.getPageIndex(), directivePage.getPageSize());
        List<Article> articles = articleMapper.frontArticlesList(directivePage.getForm());
        PageInfo<Article> pageInfo = new PageInfo<>(articles);
        directivePage.setTotal(pageInfo.getTotal());
        directivePage.setData(pageInfo.getList());
        return directivePage;
    }

    /**
     * 获取下一篇文章
     * @param articleId articleId
     * @return Article
     */
    public Article getNextArticle(Long articleId) {
        return articleMapper.getNextArticle(articleId);
    }

    /**
     * 获取上一篇文章
     * @param articleId articleId
     * @return Article
     */
    public Article getPreArticle(Long articleId) {
        return articleMapper.getPreArticle(articleId);
    }

    public void articleCommentAdd(Long articleId) {
        articleMapper.articleCommentAdd(articleId);
    }

    public void articleCommentSub(String[] idArr) {
        for (String id : idArr) {
            articleMapper.articleCommentSub(id);
        }
    }

    /**
     * 获取归档列表
     * @param articlePage articlePage
     * @return DirectivePage<HashMap<String, String>>
     */
    public DirectivePage<HashMap<String, String>> frontArchivePage(DirectivePage<HashMap<String, String>> articlePage) {
        PageHelper.startPage(articlePage.getPageIndex(), articlePage.getPageSize());
        List<Archive> archives;
        if (DynamicDataSource.dataSourceType.equals("mysql")) {
            archives = articleMapper.frontArchivePage();
        } else {
            archives = articleMapper.frontArchivePageBySqlite();
        }
        PageInfo<Archive> pageInfo = new PageInfo<>(archives);
        articlePage.setTotal(pageInfo.getTotal());
        articlePage.setData(pageInfo.getList());
        return articlePage;
    }

    public void articleViewCountAdd(Long articleId) {
        articleMapper.articleViewCountAdd(articleId);
    }

    public List<Article> getPageList() {
        return articleMapper.getPageList();
    }
}
