import m from 'mithril';
import EventBus from 'vertx3-eventbus-client';
import fn from './fn';

let bus = new EventBus(window.location.protocol + '//' + window.location.hostname + ':' + window.location.port + '/eventbus');
bus.enableReconnect(true);

let connected = false;

bus.onopen = () => {
  connected = true;
  bus.registerHandler('removedName', (err, msg) => {
    let name = msg.body;
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

class InputWithEnter {
  inputEvent(vnode) {
    return (e) => {
      if (this.name && this.name.length > 1 && e.keyCode === 13) {
        vnode.attrs.onenter(this.name);
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

const actions = [{
  glyph: 'glyphicon-remove',
  run: (name) => (e) => bus.send('removeName', name)
}]

class ListGroup {
  view(vnode) {
    return m('.list-group', vnode.attrs, vnode.children);
  }
}
class ListGroupItem {
  view(vnode) {
    return m('.list-group-item', vnode.attrs, vnode.children);
  }
}

class WikiLink {
  view(vnode) {
    m('a', {
      href: 'https://' + (vnode.attrs.lang ? +vnode.attrs.lang : 'de') +
        '.wikipedia.org/wiki/' + vnode.attrs.link
    }, vnode.attrs.text ? vnode.attrs.text : vnode.attrs.link);
  }
}

class NameList {
  view(vnode) {
    return vnode.attrs.names && vnode.attrs.names.length > 0 ?
      m(ListGroup,
        vnode.attrs.names.map(name => m(ListGroupItem, m(WikiLink, {
          link: name.name
        }), vnode.attrs.actions ? vnode.attrs.actions.map(action => {
          return m('button.pull-right', {
            onclick: action.run(name)
          }, m('i.glyphicon.' + action.glyph));
        }) : null))
      ) : m('', 'Noch keine Namen hier...');
  }
}

class OnlineBadge {
  view(vnode) {
    return vnode.attrs.connected ?
      m('.badge', {
        style: 'background-color:green'
      }, 'online') :
      m('.badge', {
        style: 'background-color:red'
      }, 'offline');
  }
}

class Layout {
  view(vnode) {
    return username ? [
      m('.container',
        m('h1', 'Die Suche nach dem heiligen Gral', ' ', m(OnlineBadge, {
          connected: connected
        })),
        m(NameList, {
          names: ownnames(names),
          actions: actions
        }),
        m(InputWithEnter),
        m('h1', m('i.glyphicon.glyphicon-heart'), ' ', 'Gemeinsamkeiten'),
        m(NameList, {
          names: duplicates(names)
        }),
        m('hr'),
        m('h2', m('i.glyphicon.glyphicon-user'), ' ', 'Teilnehmer'),
        m('ul',
          mapObj(countProps(pluck(names, 'user'))).map(kv => m('li', kv.key, ' ', m('.badge', kv.value))))
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

m.mount(document.body, {
  view: vnode => [m(Layout)]
});
