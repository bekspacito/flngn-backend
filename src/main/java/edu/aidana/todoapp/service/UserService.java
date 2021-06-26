package edu.aidana.todoapp.service;

import edu.aidana.todoapp.model.entity.Role;
import edu.aidana.todoapp.model.entity.Status;
import edu.aidana.todoapp.model.entity.User;
import edu.aidana.todoapp.repos.UserRepository;
import edu.aidana.todoapp.util.JwtUtil;
import edu.aidana.todoapp.model.dto.user.RegistrationRequest;
import edu.aidana.todoapp.model.dto.user.RegistrationResponse;
import edu.aidana.todoapp.model.dto.files.FileRecordDto;
import edu.aidana.todoapp.model.dto.user.UserDto;
import edu.aidana.todoapp.repos.RoleRepository;
import edu.aidana.todoapp.repos.StatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService implements UserDetailsService {

    private JwtUtil jwtUtil;
    private FileService fileService;

    private UserRepository userRepo;
    private StatusRepository statusRepo;
    private RoleRepository roleRepository;

    @Autowired
    public UserService(
            JwtUtil jwtUtil,
            FileService fileService,
            UserRepository userRepo,
            StatusRepository statusRepo,
            RoleRepository roleRepository)
    {
        this.jwtUtil = jwtUtil;
        this.fileService = fileService;
        this.statusRepo = statusRepo;
        this.roleRepository = roleRepository;
        this.userRepo = userRepo;
    }

    @Override
    public User loadUserByUsername(String s) throws UsernameNotFoundException {
        return userRepo.findByUsername(s).orElseThrow(() -> new UsernameNotFoundException(s));
    }

    public List<User> loadUsersByUsername(List<String> ss) {
        return userRepo.findAllByUsernameIn(ss);
    }

    public User createUser(String username, String password, String email) {
        User user = new User();

        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(email);

        user.setStatus(statusRepo.findByCode(Status.Code.ENABLED));
        user.setRoles(roleRepository.findByCodeIn(Collections.singleton(Role.Code.ROLE_USER)));

        return userRepo.save(user);
    }

    @Transactional
    public RegistrationResponse registerUser(RegistrationRequest req) {

        String username = req.getUsername();
        String password = req.getPassword();
        String email = req.getEmail();

        // Create user
        User user = createUser(username, password, email);

        // Create root folder for a user
        FileRecordDto root = fileService.prepareUserRootFolder(user);

        // If the execution reached here then everything went fine
        String token = jwtUtil.generateToken(user);

        return new RegistrationResponse(username, email, token, root.getId());
    }

    public List<UserDto> searchByName(String namechunk) {
        return userRepo.findAllByUsernameLike("%" + namechunk + "%")
                    .stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
    }

    private UserDto toDto(User user) {
        UserDto dto = new UserDto();

        dto.setUsername(user.getUsername());

        return dto;
    }

}
