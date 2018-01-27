import m from 'mithril';
import EventBus from 'vertx3-eventbus-client';
import fn from './fn';

let bus = new EventBus(window.location.protocol + '//' + window.location.hostname + ':' + window.location.port + '/eventbus');
bus.enableReconnect(true);

let connected = false;


bus.onopen = () => {
  connected = true;
  if (user){
      user.leaveGroup();
  }
  m.redraw();
};

bus.onclose = e => {
  connected = false;
  m.redraw();
};

/***
 *     _______           _______  _______  _______  _______  _______ 
 *    (  ___  )|\     /|(  ____ \(  ____ \(  ___  )(       )(  ____ \
 *    | (   ) || )   ( || (    \/| (    \/| (   ) || () () || (    \/
 *    | (___) || | _ | || (__    | (_____ | |   | || || || || (__    
 *    |  ___  || |( )| ||  __)   (_____  )| |   | || |(_)| ||  __)   
 *    | (   ) || || || || (            ) || |   | || |   | || (      
 *    | )   ( || () () || (____/\/\____) || (___) || )   ( || (____/\
 *    |/     \|(_______)(_______/\_______)(_______)|/     \|(_______/
 *                                                                   
 *     _______  _______  _______  _______  _______  _       _________
 *    (  ____ \(  ___  )(       )(       )(  ____ \( (    /|\__   __/
 *    | (    \/| (   ) || () () || () () || (    \/|  \  ( |   ) (   
 *    | |      | |   | || || || || || || || (__    |   \ | |   | |   
 *    | |      | |   | || |(_)| || |(_)| ||  __)   | (\ \) |   | |   
 *    | |      | |   | || |   | || |   | || (      | | \   |   | |   
 *    | (____/\| (___) || )   ( || )   ( || (____/\| )  \  |   | |   
 *    (_______/(_______)|/     \||/     \|(_______/|/    )_)   )_(   
 *                                                                   
 */

class Spacer {
  view(vnode){
    return m('',' ');
  }
}

class Tooltip {
    view(vnode){
        return m('.tooltiptext',vnode.attrs,vnode.children);
    }
}

class SomeThingWithTooltip {
    view(vnode) {
        return m('.mtooltip',vnode.attrs, vnode.children);
    }    
}

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
    return m('.input-group',
      vnode.attrs.icon? m('span.input-group-addon',m(Icon,{icon:vnode.attrs.icon})):null,
      m('input.form-control', {
        onkeyup: this.inputEvent(vnode),
        placeHolder: vnode.attrs.placeHolder
      })
    )
  }
}

const actions = [{
  glyph: 'remove',
  run: (name) => (e) => user.removeName(name)
}]

class ListGroup {
  view(vnode) {
    return m('.list-group', vnode.attrs, vnode.children);
  }
}

class ListGroupItem {
  view(vnode) {
    return m('a.list-group-item', vnode.attrs, vnode.children);
  }
}

class Container {
  view(vnode){
    return m('.container', vnode.attrs, vnode.children);
  }
}

class Jumbotron {
  view(vnode){
    return m('.jumbotron', vnode.attrs, vnode.children);
  }
}

class WikiLink {
  view(vnode) {
    return m('a', {
      href: 'https://' + (vnode.attrs.lang ? +vnode.attrs.lang : 'de') +
        '.wikipedia.org/wiki/' + vnode.attrs.link
    }, vnode.attrs.text ? vnode.attrs.text : vnode.attrs.link);
  }
}

class SubHeading {
    view(vnode){
        return m('h2',vnode.attrs,vnode.children);
    }
}

class Heading {
    view(vnode){
        return m('h1',vnode.attrs,vnode.children);
    }
}

class Icon {
  view(vnode) {
    return m('i.glyphicon.glyphicon-' + vnode.attrs.icon, '');
  }
}

class OnlineBadge {
  view(vnode) {
    return vnode.attrs.connected ?
      m(SomeThingWithTooltip, m('.badge', {
        style: 'background-color:green'
      }, m(Icon,{icon:'ok-circle'})),m(Tooltip,'Du bist verbunden zum Server.')) :
     m(SomeThingWithTooltip, m('.badge', {
        style: 'background-color:red'
      }, m(Icon,{icon:'ban-circle'})),m(Tooltip,'Du bist nicht verbunden zum Server.'));
  }
}

/***
 *     _______           _______  _______  _______  _______  _______ 
 *    (  ___  )|\     /|(  ____ \(  ____ \(  ___  )(       )(  ____ \
 *    | (   ) || )   ( || (    \/| (    \/| (   ) || () () || (    \/
 *    | (___) || | _ | || (__    | (_____ | |   | || || || || (__    
 *    |  ___  || |( )| ||  __)   (_____  )| |   | || |(_)| ||  __)   
 *    | (   ) || || || || (            ) || |   | || |   | || (      
 *    | )   ( || () () || (____/\/\____) || (___) || )   ( || (____/\
 *    |/     \|(_______)(_______/\_______)(_______)|/     \|(_______/
 *                                                                   
 *     _______  _______  _______  _______  _______  _       _________
 *    (  ____ \(  ___  )(       )(       )(  ____ \( (    /|\__   __/
 *    | (    \/| (   ) || () () || () () || (    \/|  \  ( |   ) (   
 *    | |      | |   | || || || || || || || (__    |   \ | |   | |   
 *    | |      | |   | || |(_)| || |(_)| ||  __)   | (\ \) |   | |   
 *    | |      | |   | || |   | || |   | || (      | | \   |   | |   
 *    | (____/\| (___) || )   ( || )   ( || (____/\| )  \  |   | |   
 *    (_______/(_______)|/     \||/     \|(_______/|/    )_)   )_(   
 *                                                                   
 */

class User {
  constructor(id) {
    this.id = id;
    this.fetchList();
  }
  fetchList() {
    bus.send('list',{usr: this.id},(err, msg)=>{
      this.groups = msg.body;
      m.redraw();
    });
  }
  enterGroup(id, update = false) {
    bus.send('enter',{usr: this.id, grp:id},(err, msg)=>{
      this.group = msg.body;
      this.groups = null;
      if (!update) {
        bus.registerHandler('grp-'+id,(err, msg)=>{
          this.enterGroup(id, true);
        });
      }
      m.redraw();
    });
  }
  leaveGroup() {
    bus.unregisterHandler('grp-'+this.group.id);
    this.group = null;
    this.fetchList();
  }
  resign(){
      if (confirm('Möchtest du die Gruppe wirklich verlassen? Du kannst wieder eingeladen werden, aber deine Vorschläge sind für immer verschwunden!'))
      bus.send("resign",{usr: this.id, grp: this.group.id},(err,response)=>{
          this.leaveGroup();
          m.redraw();
      });
  }
  propose(name) {
    if (!this.group) {
      console.error('A group must be entered to add a name.');
      return;
    }
    bus.send('proposeName',{grp: this.group.id, usr: this.id, name: name});
  }
  removeName(name) {
    if (!this.group) {
      console.error('A group must be entered to remove a name.');
      return;
    }
    bus.send('removeName',{grp: this.group.id, usr: this.id, name: name});
  }
  createGroup(name) {
    bus.send('create', {name: name, usr: this.id}, (err, res)=>this.enterGroup(res.body.grp))
  }
  addMember(email) {
    bus.send('addMember', {usr: this.id, grp: this.group.id, email: email});
  }
  setUserName(name) {
      if (name && name.length > 0)
    bus.send('setUserName', {usr: this.id, grp: this.group.id, name:name},(err,result)=>{
        // TODO check for duplicates here
    });
  }
  upgrade(memberName) {
    if (confirm('Soll ich diesen Benutzer wirklich zum Administrator erheben? Das kann zu Ehekrach führen ;-)!'))
    bus.send('upgrade', {usr: this.id, grp: this.group.id, member:memberName})
  }
}

/***
 *     _______           _______  _______  _______  _______  _______ 
 *    (  ___  )|\     /|(  ____ \(  ____ \(  ___  )(       )(  ____ \
 *    | (   ) || )   ( || (    \/| (    \/| (   ) || () () || (    \/
 *    | (___) || | _ | || (__    | (_____ | |   | || || || || (__    
 *    |  ___  || |( )| ||  __)   (_____  )| |   | || |(_)| ||  __)   
 *    | (   ) || || || || (            ) || |   | || |   | || (      
 *    | )   ( || () () || (____/\/\____) || (___) || )   ( || (____/\
 *    |/     \|(_______)(_______/\_______)(_______)|/     \|(_______/
 *                                                                   
 *     _______  _______  _______  _______  _______  _       _________
 *    (  ____ \(  ___  )(       )(       )(  ____ \( (    /|\__   __/
 *    | (    \/| (   ) || () () || () () || (    \/|  \  ( |   ) (   
 *    | |      | |   | || || || || || || || (__    |   \ | |   | |   
 *    | |      | |   | || |(_)| || |(_)| ||  __)   | (\ \) |   | |   
 *    | |      | |   | || |   | || |   | || (      | | \   |   | |   
 *    | (____/\| (___) || )   ( || )   ( || (____/\| )  \  |   | |   
 *    (_______/(_______)|/     \||/     \|(_______/|/    )_)   )_(   
 *                                                                   
 */

class Header {
  view(vnode){
    return m(Jumbotron,
      m('h1.text-center',m(OnlineBadge, {
        connected: connected
      }), ' Nayouchi ', m(OnlineBadge, {
        connected: connected
      })),
      m('img.center-block', {width:'10%', src:'/storch.png'})
    );
  }
}


class NameList {
  view(vnode) {
    return m(ListGroup,
      vnode.attrs.onadd? m(ListGroupItem,
        m(InputWithEnter,{
          icon:'plus',
          onenter:n=>vnode.attrs.onadd(n)
        })):null, vnode.attrs.names && vnode.attrs.names.length > 0 ?
        vnode.attrs.names.map(name_ => m(ListGroupItem, m(WikiLink, {
          link: name_
        }), vnode.attrs.actions ? vnode.attrs.actions.map(action => {
          return m('button.btn.btn-xs.btn-default.pull-right', {
            onclick: action.run(name_)
          }, m(Icon,{icon: action.glyph}));
        }) : null))
       : m('', 'Noch keine Namen hier...'));
  }
}


class GroupView {
  view(vnode){
    let grp = vnode.attrs.group;
    return m(ListGroupItem, { onclick:e=> user.enterGroup(grp.id) },
      m(Icon, {icon:'star'}), ' ',
      grp.name,
      ' ( ', grp.others.map(o=>o.name).join(', '), ' ) ',
      m('.badge', grp.me.nominations.length),
      m('.pull-right',m(Icon,{icon:'arrow-right'}))
    );
  }
}

class GroupListView {
  view(vnode){
    return m(ListGroup,
      vnode.attrs.user.groups.map(group=>m(GroupView,{group})),
      vnode.attrs.onadd? m(ListGroupItem,
        m(InputWithEnter,{
          icon:'plus',
          placeHolder: 'Neue Gruppe zur Namenssuche starten',
          onenter:n=>vnode.attrs.onadd(n)
        })):null
    );
  }
}

class TeamView {
  view(vnode){
    return [
      m(SubHeading,m(Icon, {icon: 'bullhorn'}),' ','Der Vor-Schlägertrupp'),
      m(ListGroup,
        vnode.attrs.user.group.initiators.map(member => {
          return m(ListGroupItem,
            m(Icon,{icon:'user',style:'text-color:red'}),
            ' ',
            member,
            ' ',
            m(SomeThingWithTooltip, m(Icon,{icon:'certificate'}),m(Tooltip,'Dieser vielzackige Stern ist das Zeichen für die große Macht der Gruppenentscheider.')), 
            vnode.attrs.user.group.me.name === member? m('button.btn.btn-xs.pull-right',{onclick:e=>user.resign()},m(Icon,{icon:'chevron-right'})):null
            );
        }),
        vnode.attrs.user.group.proposers.map(member=>{
          return m(ListGroupItem,
            m(Icon,{icon:'user',style:'text-color:red'}),
            ' ',
            member.length > 0? member: 'Anonymous der Große (bislang)',
          ' ',
          user.group.me.role === 'INITIATOR' ? m('button.btn-xs.btn-default',{
                 onclick:e => user.upgrade(member)
               },m(Icon,{
            icon:'chevron-up',
          })):null, 
            vnode.attrs.user.group.me.name === member? m('button.btn.btn-xs.pull-right',{onclick:e=>user.resign()},m(Icon,{icon:'chevron-right'})):null
        );
        }),
        user.group.me.role === 'INITIATOR'? m(ListGroupItem,
          m(InputWithEnter,{
            icon:'envelope',
            placeHolder: 'Lade Freunde ein, oder erinnere sie daran, Vorschläge zu machen',
            onenter:n=>user.addMember(n)
          })):null
        )
    ];
  }
}

class NameListView {
  view(vnode){
    return [
      m(Heading,m(SomeThingWithTooltip, m('button.btn.btn-primary',{
        onclick:e => user.leaveGroup()
      },m(Icon,{icon:'arrow-left'})),m(Tooltip,'Zurück zur Gruppenübersicht')), ' ' ,user.group.name),
      m(SubHeading, m(Icon,{icon:'heart'}),' ',m(SomeThingWithTooltip,'Treffer',m(Tooltip,'Treffer sind alle Übereinstimmungen zwischen den Entscheidern')),' von ', user.group.initiators.join(', ')),
       m(NameList,{
         names: user.group.duplicates
//           actions: [{ glyph: 'ok', run: (name)=> (e) => console.log(name)}]
      }),
      m(SubHeading,'Meine Vorschläge '/*,user.group.me.role === 'INITIATOR'?'Initiator':' Vorschläger'*/),
    m(NameList,{
      onadd: name => user.propose(name),
      names: user.group.me.nominations,
      actions: actions
    }),      user.group.proposersNominations.length>0?[
     m(SubHeading,'Tipps von ', user.group.proposers.join(', ')),
        m(NameList,{
          names: user.group.proposersNominations,
          actions: [{ glyph: 'ok', run: (name)=> (e) =>  user.propose(name)}]
        })]:null,
        m(TeamView, {user: user})
];
  }
}

class Layout{
  view(vnode) {
    return m('body',{}, m(Container,[
      m(Header),
      vnode.attrs.user && vnode.attrs.user.groups ?
      m(GroupListView, {user: vnode.attrs.user, onadd:grpName=> user.createGroup(grpName)}):null,
      vnode.attrs.user && vnode.attrs.user.group && vnode.attrs.user.group.me.name.length > 0?
      m(NameListView, {user: vnode.attrs.user}):vnode.attrs.user && vnode.attrs.user.group?[
        m(SubHeading, 'Bevor man Namen sagen darf, sag mir erst einmal deinen.'),
        m(InputWithEnter,{icon:'user', onenter:name=>user.setUserName(name)})
      ]:null,
      
//      m('pre.well.success',JSON.stringify(vnode.attrs.user, undefined,2))
    ]))
  }
}

class SignUp {
  constructor(vnode) {
    this.mailSent = false;
  }
  sendMail(email){
    bus.send('signup',{
      email: email
    }      , (e)=>{
      this.email = email;
              this.mailSent = true;
              m.redraw();
            });
  }
  view(vnode) {
    return m(Container,
      m(Header),
      this.mailSent?
      m('.alert.alert-success','Eine Email mit einem Speziallink wurde gesendet an ' + this.email):[
      m('.row',m('.col-md-6.col-xs-12', m('p', `Hallo Fremder,`,m('br'),`
       diese Applikation weiss
        noch nichts über dich und wird das
         auch in Zukunft nicht tun. Trotzdem
         lassen wir dich hier deine Email-Adresse eingeben. Häh? Naja:
        Die Adresse wird nicht gespeichert. Alles was passiert ist, dass du
        einen eindeutigen Link erhälst, damit du erstens irgendwie eindeutig bist
        und zweitens keinen Benutzernamen vergessen kannst - du wirst nämlich unter Umständen gleich mehrere haben.`)),
        m('.col-md-6.col-xs-12', m('p',`
        Wer das nicht glaubt, kann bei `,m('a[href=https://github.com/abulvenz/nayouchi/tree/enterprise-edition]',m(Icon,{icon:'link'}), 'github'),` nachschauen.
        `))),
        m(Spacer,{style:'height:300px'}),
        m(InputWithEnter,{
        icon:'envelope',
      placeHolder: 'Wenn dich niemand eingeladen hat, oder du deinen Link verloren hast, bitte deine Emailadresse eingeben.',
        onenter:email => this.sendMail(email)
      })]
    )
  }
}


let pathFracs = window.location.pathname.substring(1).split('/');

let user = null;

var createUser = () => {
  if (!connected) {
    setTimeout(createUser,200);
  } else {
    user = new User(pathFracs[1]);
    m.redraw();
  }
}

if (pathFracs.length > 1)
  createUser();


m.mount(document.body, {
  view: vnode => pathFracs.length > 1?[m(Layout,{user})]:[m(SignUp)]
});
