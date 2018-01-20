import m from 'mithril';
import EventBus from 'vertx3-eventbus-client'

let bus = new EventBus(window.location.protocol + '//' + window.location.hostname + ':' + window.location.port + '/eventbus');
let names = [];

bus.enableReconnect(true);

let connected = false;

bus.onopen = () => {
    connected = true;
    console.log('connected');
    bus.registerHandler('names', (name, ef) => {
        names.push(ef.body);
        m.redraw();
    });
    bus.registerHandler('removedName', (err, msg) => {
        console.log('removedName', msg)
        let name = msg.body;
        names = names.filter(n => !(name.name === n.name && name.user === n.user));
        m.redraw();
    });
    bus.send('fetch', 'doit', (err, result) => {
        names = result.body;
        m.redraw();
    });
    m.redraw();
};

bus.onclose = e => {
    connected = false;
    console.log('disconnected');
    m.redraw();
};

let username = localStorage.getItem("namen-name");

const usersnames = (names, username) => {
    return names.filter(n => n.user === username);
};

const ownnames = (names) => usersnames(names, username);

const propList = obj => {
    var properties = [];
    for (let p in obj) properties.push(p);
    return properties;
}

const contains = (arr, e) => arr.indexOf(e) >= 0;

const duplicates = names => {
    let pc = countProps(pluck(names, 'name'));
    return propList(pc).
        filter(p => pc[p] > 1).
        map(e => { return { name: e } });
};

const countProps = (arr) => {
    return arr.reduce((acc, curr) => {
        acc[curr] = acc[curr] + 1 || 1;
        return acc;
    }, {});
}

const pluck = (arr, prop) => arr.map(e => e[prop]);

const mapObj = (obj) => {
    let props = propList(obj);
    return props.map(prop => { return { key: prop, value: obj[prop] }; });
}

class InputWithEnter {
    inputEvent(vnode) {
        return (e) => {
            console.log(e);
            if (this.name && this.name.length > 1 && e.keyCode === 13) {
                bus.send("sendName", {
                    name: this.name,
                    user: username
                });
                e.target.value = ('');
            } else {
                this.name = e.target.value;
            }
        }
    }
    view(vnode) {
        return m('input.form-control', {
            onkeyup: this.inputEvent(vnode)
        })
    }
}

const actions = [
    {
        glyph: 'glyphicon-remove',
        run: (name) => (e) => bus.send('removeName', name)
    }
]

class ListGroup {
    view(vnode){
        return m('.list-group', vnode.attrs, vnode.children);
    }
}
class ListGroupItem {
    view(vnode){
        return m('.list-group-item', vnode.attrs, vnode.children);
    }
}

class NameList {
    view(vnode) {
        return vnode.attrs.names && vnode.attrs.names.length > 0 ?
            m(ListGroup,
                vnode.attrs.names.map(name => m(ListGroupItem, m('a', {
                    href: 'https://de.wikipedia.org/wiki/' + name.name
                }, name.name), vnode.attrs.actions ? vnode.attrs.actions.map(action => {
                    return m('button.pull-right', {
                        onclick: action.run(name)
                    }, m('i.glyphicon.' + action.glyph));
                }) : null))
            ) : m('', 'Noch keine Namen hier...');
    }
}

class OnlineBadge {
    view(vnode) {
        return vnode.attrs.connected?
         m('.badge',{style:'background-color:green'}, 'online'):
         m('.badge',{style:'background-color:red'}, 'offline');
    }
}

class Layout {
    view(vnode) {
        return username ? [
            m('.container',
                m('h1', 'Die Suche nach dem heiligen Gral',' ',m(OnlineBadge,{connected:connected})),
                m(NameList, { names: ownnames(names), actions: actions }),
                m(InputWithEnter),
                m('h1', m('i.glyphicon.glyphicon-heart'), ' ', 'Gemeinsamkeiten'),
                m(NameList, { names: duplicates(names) }),
                m('hr'),
                m('h2', m('i.glyphicon.glyphicon-user'), ' ', 'Teilnehmer'),
                m('ul',
                    mapObj(countProps(pluck(names, 'user'))).
                        map(kv => m('li', kv.key, ' ', m('.badge', kv.value))))
            )
        ] : [
                m('.container',
                    m('h1', 'Wie ist Dein Name?'),
                    m('input.form-control', {
                        placeHolder: 'Name',
                        onchange: ev => {
                            username = ev.target.value;
                            localStorage.setItem("namen-name", username);
                            console.log(username)
                        }
                    }),
                    m('button.btn.btn-success', 'Login')
                )
            ]
    }
}

m.mount(document.body, { view: vnode => [m(Layout)] });