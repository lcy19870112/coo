package coo.core.security.service;

import java.util.List;

import javax.annotation.Resource;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.DisabledAccountException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.hibernate.Criteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.springframework.transaction.annotation.Transactional;

import coo.base.exception.UncheckedException;
import coo.base.model.BitCode;
import coo.base.model.Page;
import coo.base.util.Assert;
import coo.base.util.StringUtils;
import coo.core.hibernate.dao.Dao;
import coo.core.hibernate.search.FullTextCriteria;
import coo.core.model.SearchModel;
import coo.core.security.annotations.AutoFillResourceEntity;
import coo.core.security.constants.AdminIds;
import coo.core.security.entity.ActorEntity;
import coo.core.security.entity.OrganEntity;
import coo.core.security.entity.RoleEntity;
import coo.core.security.entity.UserEntity;
import coo.core.security.entity.UserSettingsEntity;
import coo.core.security.exception.NotLogonException;
import coo.core.security.permission.AdminPermission;
import coo.core.security.permission.PermissionConfig;

/**
 * 安全模块抽象服务类。
 * 
 * @param <O>
 *            机构类型
 * @param <U>
 *            用户类型
 * @param <R>
 *            角色类型
 * @param <A>
 *            职务类型
 * @param <S>
 *            用户设置类型
 */
public abstract class AbstractSecurityService<O extends OrganEntity<O, U, A>, U extends UserEntity<U, A, S>, R extends RoleEntity<U, A>, A extends ActorEntity<O, U, R>, S extends UserSettingsEntity<A>> {
	@Resource
	protected Dao<O> organDao;
	@Resource
	protected Dao<U> userDao;
	@Resource
	protected Dao<R> roleDao;
	@Resource
	protected Dao<A> actorDao;
	@Resource
	protected Dao<S> userSettingsDao;
	@Resource
	protected LoginRealm loginRealm;
	@Resource
	protected PermissionConfig permissionConfig;
	@Resource
	protected BnLogger bnLogger;

	/**
	 * 登录。
	 * 
	 * @param username
	 *            用户名
	 * @param password
	 *            密码
	 */
	public void signIn(String username, String password) {
		try {
			AuthenticationToken token = new UsernamePasswordToken(username,
					password);
			Subject subject = SecurityUtils.getSubject();
			subject.login(token);
			loginRealm.clearCache();
			bnLogger.log(username, "登录系统。");
		} catch (DisabledAccountException de) {
			throw new UncheckedException("用户被禁用。", de);
		} catch (UnknownAccountException ue) {
			throw new UncheckedException("用户不存在。", ue);
		} catch (IncorrectCredentialsException ie) {
			throw new UncheckedException("密码错误。", ie);
		} catch (AuthenticationException ae) {
			throw new UncheckedException("登录失败。", ae);
		}
	}

	/**
	 * 退出登录。
	 */
	public void signOut() {
		loginRealm.clearCache();
		SecurityUtils.getSubject().logout();
	}

	/**
	 * 获取指定ID的机构。
	 * 
	 * @param id
	 *            机构ID
	 * @return 返回指定ID的机构。
	 */
	@Transactional(readOnly = true)
	public O getOrgan(String id) {
		return organDao.get(id);
	}

	/**
	 * 新增机构。
	 * 
	 * @param organ
	 *            机构
	 */
	@Transactional
	@AutoFillResourceEntity
	public void createOrgan(O organ) {
		Assert.notNull(organ.getParent(), "新增机构时必须指定上级机构。");
		organDao.save(organ);
	}

	/**
	 * 更新机构。
	 * 
	 * @param organ
	 *            机构
	 */
	@Transactional
	@AutoFillResourceEntity
	public void updateOrgan(O organ) {
		organDao.merge(organ);
	}

	/**
	 * 删除机构。
	 * 
	 * @param id
	 *            机构ID
	 */
	@Transactional
	public void deleteOrgan(String id) {
		organDao.remove(id);
	}

	/**
	 * 获取指定ID的角色。
	 * 
	 * @param roleId
	 *            角色ID
	 * @return 返回指定ID的角色。
	 */
	@Transactional(readOnly = true)
	public R getRole(String roleId) {
		return roleDao.get(roleId);
	}

	/**
	 * 获取所有角色列表。
	 * 
	 * @return 返回所有角色列表。
	 */
	@SuppressWarnings("unchecked")
	@Transactional(readOnly = true)
	public List<R> getAllRole() {
		Criteria criteria = roleDao.createCriteria();
		criteria.addOrder(Order.asc("createDate"));
		return criteria.list();
	}

	/**
	 * 新增角色。
	 * 
	 * @param role
	 *            角色
	 */
	@Transactional
	@AutoFillResourceEntity
	public void createRole(R role) {
		roleDao.save(role);
	}

	/**
	 * 更新角色。
	 * 
	 * @param role
	 *            角色
	 */
	@Transactional
	@AutoFillResourceEntity
	public void updateRole(R role) {
		roleDao.merge(role);
	}

	/**
	 * 获取当前登录用户。
	 * 
	 * @return 返回当前登录用户。
	 */
	@Transactional(readOnly = true)
	public U getCurrentUser() {
		try {
			String userId = (String) SecurityUtils.getSubject().getPrincipal();
			return userDao.get(userId);
		} catch (Exception e) {
			throw new NotLogonException();
		}
	}

	/**
	 * 分页全文搜索用户。
	 * 
	 * @param searchModel
	 *            搜索条件
	 * @return 返回全文搜索用户分页对象。
	 */
	@Transactional(readOnly = true)
	public Page<U> searchUser(SearchModel searchModel) {
		FullTextCriteria criteria = userDao.createFullTextCriteria();
		criteria.setKeyword(searchModel.getKeyword());
		criteria.addSortDesc("createDate", SortField.LONG);

		// 将系统管理员从搜索的用户结果中排除
		BooleanQuery bq = new BooleanQuery();
		bq.add(new TermQuery(new Term("id", AdminIds.USER_ID)), Occur.MUST_NOT);
		bq.add(new WildcardQuery(new Term("id", "*")), Occur.MUST);
		criteria.setLuceneQuery(bq, Occur.MUST);

		return userDao.searchPage(criteria, searchModel.getPageNo(),
				searchModel.getPageSize());
	}

	/**
	 * 获取指定ID的用户。
	 * 
	 * @param id
	 *            用户ID
	 * @return 返回指定ID的用户。
	 */
	@Transactional(readOnly = true)
	public U getUser(String id) {
		return userDao.get(id);
	}

	/**
	 * 获取指定用户名的用户。
	 * 
	 * @param username
	 *            用户名
	 * @return 返回指定用户名的用户。
	 */
	@Transactional(readOnly = true)
	public U getUserByUsername(String username) {
		return userDao.findUnique("username", username);
	}

	/**
	 * 新增用户。
	 * 
	 * @param user
	 *            用户
	 */
	@Transactional
	@AutoFillResourceEntity
	public void createUser(U user) {
		Assert.isTrue(userDao.isUnique(user, "username"),
				"用户名[" + user.getUsername() + "]已存在。");
		if (StringUtils.isEmpty(user.getPassword())) {
			user.setPassword(loginRealm
					.encryptPassword(AdminPermission.DEFAULT_PASSWORD));
		}
		userDao.save(user);

		S settings = user.getSettings();

		A defaultActor = settings.getDefaultActor();
		defaultActor.setUser(user);
		defaultActor.autoFillIn();
		actorDao.save(defaultActor);

		settings.setId(user.getId());
		userDao.merge(user);
	}

	/**
	 * 更新用户。
	 * 
	 * @param user
	 *            用户
	 */
	@Transactional
	@AutoFillResourceEntity
	public void updateUser(U user) {
		Assert.isTrue(userDao.isUnique(user, "username"),
				"用户名[" + user.getUsername() + "]已存在。");
		userDao.merge(user);
	}

	/**
	 * 删除指定ID的用户。
	 * 
	 * @param id
	 *            用户ID
	 */
	@Transactional
	public void deleteUser(String id) {
		userDao.remove(id);
	}

	/**
	 * 启用用户。
	 * 
	 * @param userId
	 *            用户ID
	 */
	@Transactional
	public void enableUser(String userId) {
		U user = getUser(userId);
		user.setEnabled(true);
		userDao.merge(user);
	}

	/**
	 * 禁用用户。
	 * 
	 * @param userId
	 *            用户ID
	 */
	@Transactional
	public void disableUser(String userId) {
		U user = getUser(userId);
		user.setEnabled(false);
		userDao.merge(user);
	}

	/**
	 * 重置密码。
	 * 
	 * @param managePassword
	 *            管理员密码
	 * @param userId
	 *            重置用户ID
	 */
	@Transactional
	public void resetPassword(String managePassword, String userId) {
		Assert.isTrue(loginRealm.checkPassword(managePassword, getCurrentUser()
				.getPassword()), "管理员密码错误。");
		U user = getUser(userId);
		user.setPassword(loginRealm
				.encryptPassword(AdminPermission.DEFAULT_PASSWORD));
		userDao.merge(user);
	}

	/**
	 * 修改密码。
	 * 
	 * @param oldPwd
	 *            原密码
	 * @param newPwd
	 *            新密码
	 */
	@Transactional
	public void changePassword(String oldPwd, String newPwd) {
		U user = getCurrentUser();
		Assert.isTrue(loginRealm.checkPassword(oldPwd, user.getPassword()),
				"原密码错误。");
		user.setPassword(loginRealm.encryptPassword(newPwd));
		userDao.merge(user);
	}

	/**
	 * 获取指定ID的职务。
	 * 
	 * @param actorId
	 *            职务ID
	 * @return 返回指定ID的职务。
	 */
	@Transactional(readOnly = true)
	public A getActor(String actorId) {
		return actorDao.get(actorId);
	}

	/**
	 * 新增职务。
	 * 
	 * @param actor
	 *            职务
	 */
	@Transactional
	@AutoFillResourceEntity
	public void createActor(A actor) {
		actorDao.save(actor);
	}

	/**
	 * 更新职务。
	 * 
	 * @param actor
	 *            职务
	 */
	@Transactional
	@AutoFillResourceEntity
	public void updateActor(A actor) {
		actorDao.save(actor);
	}

	/**
	 * 删除指定ID的职务。
	 * 
	 * @param actorId
	 *            职务ID
	 */
	@Transactional
	public void deleteActor(String actorId) {
		A actor = getActor(actorId);
		Assert.isTrue(!actor.isDefaultActor(), "该职务被设置为用户默认职务无法删除。");
		actorDao.remove(actor);
	}

	/**
	 * 当前登录用户切换到指定的职务。
	 * 
	 * @param actorId
	 *            职务ID
	 */
	@Transactional
	public void changeActor(String actorId) {
		U currentUser = getCurrentUser();
		A actor = getActor(actorId);
		if (!currentUser.getActors().contains(actor)) {
			throw new UncheckedException("试图切换为非法的职务");
		}
		currentUser.getSettings().setDefaultActor(actor);
		currentUser = userDao.merge(currentUser);
		loginRealm.clearCache();
	}

	/**
	 * 查找具有指定权限的用户。
	 * 
	 * @param permissionCode
	 *            权限编码
	 * @return 返回具有指定权限的用户列表。
	 */
	@Transactional(readOnly = true)
	public List<U> findUserByPermission(String permissionCode) {
		return findUserByPermissions(new String[] { permissionCode },
				new String[] {});
	}

	/**
	 * 查找具有指定权限的用户。仅限于用户的某个职务同时满足权限条件，而非用户的所有职务组合满足权限条件。
	 * 
	 * @param includePermissionCodes
	 *            包含权限编码
	 * @return 返回满足权限条件的用户集合。
	 */
	@Transactional(readOnly = true)
	public List<U> findUserByPermissions(String[] includePermissionCodes) {
		return findUserByPermissions(includePermissionCodes, new String[] {});
	}

	/**
	 * 查找具有指定权限的用户。仅限于用户的某个职务同时满足权限条件，而非用户的所有职务组合满足权限条件。
	 * 
	 * @param includePermissionCodes
	 *            包含权限编码
	 * @param excludePermissionCodes
	 *            不包含权限编码
	 * @return 返回满足权限条件的用户集合。
	 */
	@Transactional(readOnly = true)
	@SuppressWarnings("unchecked")
	public List<U> findUserByPermissions(String[] includePermissionCodes,
			String[] excludePermissionCodes) {
		List<Integer> trueBits = permissionConfig
				.getPermissionIds(includePermissionCodes);
		List<Integer> falseBits = permissionConfig
				.getPermissionIds(excludePermissionCodes);
		BitCode permissionCode = new BitCode().getQueryBitCode(
				trueBits.toArray(new Integer[] {}),
				falseBits.toArray(new Integer[] {}));
		Criteria criteria = userDao.createCriteria();
		criteria.createAlias("actors.role", "role");
		criteria.add(Restrictions.like("role.permissions",
				permissionCode.toString()));
		criteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		return criteria.list();
	}

	/**
	 * 获取系统根机构。
	 * 
	 * @return 返回系统根机构。
	 */
	public O getRootOrgan() {
		return organDao.get(AdminIds.ORGAN_ID);
	}

	/**
	 * 获取系统管理员用户。
	 * 
	 * @return 返回系统管理员用户。
	 */
	public U getAdminUser() {
		return userDao.get(AdminIds.USER_ID);
	}

	/**
	 * 获取系统管理员角色。
	 * 
	 * @return 返回系统管理员角色。
	 */
	public R getAdminRole() {
		return roleDao.get(AdminIds.ROLE_ID);
	}
}