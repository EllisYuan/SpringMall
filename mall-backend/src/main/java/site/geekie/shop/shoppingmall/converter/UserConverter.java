package site.geekie.shop.shoppingmall.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import site.geekie.shop.shoppingmall.dto.RegisterDTO;
import site.geekie.shop.shoppingmall.entity.UserDO;
import site.geekie.shop.shoppingmall.vo.UserVO;

import java.util.List;

/**
 * 用户实体转换器
 * 使用 MapStruct 将 UserDO 转换为 UserVO
 *
 * 转换规则：
 *   - UserVO 中不包含 password 字段，自动过滤敏感信息
 *   - 自动映射同名字段：id, username, email, phone, avatar, role, status, createdAt
 */
@Mapper(componentModel = "spring")
public interface UserConverter {

    /**
     * 将 UserDO 转换为 UserVO
     * 自动映射同名字段，UserVO 中无 password 字段，自动排除
     *
     * @param user 用户实体
     * @return 用户视图对象
     */
    UserVO toVO(UserDO user);

    /**
     * 批量转换 UserDO 列表为 UserVO 列表
     *
     * @param users 用户实体列表
     * @return 用户视图对象列表
     */
    List<UserVO> toVOList(List<UserDO> users);

    /**
     * 将 RegisterDTO 转换为 UserDO（注册场景）
     * password 需 Service 层用 PasswordEncoder.encode() 加密后单独赋值；
     * avatar、role、status、id、createdAt、updatedAt 由 Service 层或数据库处理
     *
     * @param dto 注册请求DTO
     * @return 用户实体DO（password、role、status 需 Service 层单独赋值）
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserDO toDO(RegisterDTO dto);
}
