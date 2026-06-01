package site.geekie.shop.shoppingmall.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import site.geekie.shop.shoppingmall.entity.UserDO;
import site.geekie.shop.shoppingmall.mapper.UserMapper;

/**
 * Spring Security用户详情服务实现类
 * 实现UserDetailsService接口，为Spring Security提供用户认证信息加载功能
 *
 * 该服务在用户登录时被Spring Security调用，负责：
 *   - 根据用户名从数据库查询用户信息
 *   - 将用户实体转换为Spring Security所需的UserDetails对象
 *   - 处理用户不存在的异常情况
 *
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    // 用户数据访问对象，用于从数据库查询用户信息
    private final UserMapper userMapper;

    /**
     * 根据账号（用户名或邮箱）加载用户详情
     * Spring Security在认证过程中调用此方法获取用户信息
     * 包含 '@' 时按邮箱匹配，否则按用户名匹配
     *
     * @param account 用户名或邮箱地址
     * @return UserDetails 用户详情对象，包含认证所需的用户信息
     * @throws UsernameNotFoundException 当用户不存在时抛出此异常
     */
    @Override
    public UserDetails loadUserByUsername(String account) throws UsernameNotFoundException {
        UserDO user = userMapper.findByAccount(account);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + account);
        }
        return new SecurityUser(user);
    }
}
