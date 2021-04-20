package edu.myrza.todoapp.controller;

import edu.myrza.todoapp.model.dto.share.RefuseShareReq;
import edu.myrza.todoapp.model.dto.share.ShareDto;
import edu.myrza.todoapp.model.dto.share.ShareReq;
import edu.myrza.todoapp.model.dto.share.UnShareReq;
import edu.myrza.todoapp.model.entity.User;
import edu.myrza.todoapp.service.ShareService;
import edu.myrza.todoapp.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

@RestController
public class ShareController {

    private UserService userService;
    private ShareService shareService;

    @Autowired
    public ShareController(ShareService shareService, UserService userService) {
        this.shareService = shareService;
        this.userService = userService;
    }

    @PostMapping("/share")
    public List<ShareDto> share(Principal principal, @RequestBody ShareReq req) {

        // 1. Get this method call owner
        User owner = userService.loadUserByUsername(principal.getName());

        // 2. Get other users
        if(req.getUserNames().isEmpty())
            return Collections.emptyList();

        List<User> users = userService.loadUsersByUsername(req.getUserNames());

        // 3. Share
        return shareService.share(owner, users, req.getFileIds());
    }

    @PostMapping("/unshare")
    public List<ShareDto> unShare(Principal principal, @RequestBody UnShareReq req) {

        // 1. Get this method call author
        User me = userService.loadUserByUsername(principal.getName());

        // 2. Get other users
        if(req.getUserNames().isEmpty())
            return Collections.emptyList();

        List<User> users = userService.loadUsersByUsername(req.getUserNames());

        // 3. Share
        return shareService.unShare(me, users, req.getFileIds());
    }

    @PostMapping("/share/refuse")
    public List<ShareDto> refuseShare(Principal principal, @RequestBody RefuseShareReq req) {

        // 1. Get this method call author
        User me = userService.loadUserByUsername(principal.getName());

        // 3. Share
        return shareService.refuseShare(me, req.getFileIds());
    }

}
