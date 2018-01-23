/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.vertx.starter;

import io.vertx.core.json.JsonObject;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author iceman
 */
public class NameSearchMember {

    String name = "";
    String id = "";
    List<String> nominations = new LinkedList<>();
    Role role = Role.PROPOSER;

    JsonObject toPublic() {
        JsonObject result = new JsonObject()
                .put("name", name)
                .put("role", role);
        return result;
    }

    JsonObject toJson() {
        JsonObject result = new JsonObject()
                .put("name", name)
                .put("nominations", nominations)
                .put("role", role);
        return result;
    }

}
