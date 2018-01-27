/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.vertx.starter;

import io.vertx.core.json.JsonObject;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/**
 *
 * @author iceman
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class NameSearchGroup {

    public String name = "";
    public String id = "";

    public List<NameSearchMember> members = new LinkedList<>();

    JsonObject toPublic(String userID) {
        NameSearchMember me = members.stream()
                .filter(member -> member.id.equals(userID))
                .findAny().orElseThrow(() -> new RuntimeException("No such user in group"));

        List<JsonObject> others = members.stream()
                .filter(member -> !member.id.equals(userID))
                .map(member -> member.toPublic())
                .collect(Collectors.toList());

        JsonObject result = new JsonObject()
                .put("name", name)
                .put("id", id)
                .put("me", me.toJson())
                .put("others", others);
        return result;
    }

    JsonObject toCurrent(String userID) {
        NameSearchMember me = members.stream()
                .filter(member -> member.id.equals(userID))
                .findAny().orElseThrow(() -> new RuntimeException("No such user in group"));
        List<String> others = members.stream()
                .filter(member -> !member.id.equals(userID))
                .filter(member -> member.role == Role.PROPOSER)
                .flatMap(member -> member.nominations.stream())
                .distinct()
                .collect(Collectors.toList());
        List<String> proposerNames = members.stream()
                .filter(member -> member.role == Role.PROPOSER)
                .map(member -> member.name)
                .distinct()
                .collect(Collectors.toList());
        List<String> initiatorNames = members.stream()
                .filter(member -> member.role == Role.INITIATOR)
                .map(member -> member.name)
                .collect(Collectors.toList());
        List<String> duplicates = members.stream()
                .filter(member -> member.role == Role.INITIATOR)
                .flatMap(member -> member.nominations.stream().distinct())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(e -> e.getKey())
                .collect(Collectors.toList());
        Map<String, Integer> proposalsPerUser = members.stream()
                .collect(Collectors.toMap(member -> member.name, member -> member.nominations.size()));
        JsonObject result = new JsonObject()
                .put("name", name)
                .put("id", id)
                .put("me", me.toJson())
                .put("proposers", proposerNames)
                .put("proposersNominations", others)
                .put("initiators", initiatorNames)
                .put("stats", proposalsPerUser)
                .put("duplicates", duplicates);
        return result;
    }
}
