package edu.gemini.seqexec.web.client.components

import edu.gemini.seqexec.web.client.model.{SeqexecCircuit, WSConnect}
import edu.gemini.seqexec.web.client.model.InstrumentNames
import edu.gemini.seqexec.web.client.model.SeqexecUIModel
import edu.gemini.seqexec.web.client.model.Pages._
import edu.gemini.seqexec.web.client.model.NavigateSilentTo
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.{Callback, ScalaComponent}
import diode.ModelRO
import diode.react.ModelProxy

object SeqexecMain {
  private val component = ScalaComponent.builder[ModelProxy[SeqexecUIModel]]("SeqexecUI")
    .stateless
    .render(_ =>
      <.div(
        NavBar()
        // navLocationConnect(_ => QueueArea()),
        // r.render(),
        //lbConnect(LoginBox.apply)
      )
    ).build

  def apply(s: ModelProxy[SeqexecUIModel]) = component(s)
}

/**
  * Top level UI component
  */
object SeqexecUI {
  // private val lbConnect = SeqexecCircuit.connect(_.loginBox)
  // private val navLocationConnect = SeqexecCircuit.connect(_.navLocation)
  private val uiConnect = SeqexecCircuit.connect(_.uiModel)

  case class RouterProps(page: InstrumentPage, router: RouterCtl[InstrumentPage])

  def router: Router[SeqexecPages] = {
    val routerConfig = RouterConfigDsl[SeqexecPages].buildConfig { dsl =>
      import dsl._

      def layout(c: RouterCtl[SeqexecPages], r: Resolution[SeqexecPages]) =
        <.div(r.render()).render

      (emptyRule
      | staticRoute(root, Root) ~> renderR(r => uiConnect(SeqexecMain.apply))
      | dynamicRoute(("/" ~ string("[a-zA-Z0-9-]+") ~ "/" ~ string("[a-zA-Z0-9-]+").option).caseClass[InstrumentPage]) {
          case x @ InstrumentPage(i, _) if InstrumentNames.instruments.list.toList.contains(i) => x
        } ~> dynRenderR((p, r) => uiConnect(SeqexecMain.apply))
      )
        .notFound(redirectToPage(Root)(Redirect.Push))
        // Runtime verification that all pages are routed
        .verify(Root, InstrumentNames.instruments.list.toList.map(i => InstrumentPage(i, None)): _*)
        .onPostRender((_, next) =>
          Callback.when(next != SeqexecCircuit.zoom(_.uiModel.navLocation).value)(Callback.log("post render" + next.toString) >> Callback(SeqexecCircuit.dispatch(NavigateSilentTo(next)))))
        .renderWith(layout)
        .logToConsole
    }

    val (router, routerLogic) = Router.componentAndLogic(BaseUrl.fromWindowOrigin, routerConfig)

    def navigated(page: ModelRO[SeqexecPages]): Unit = {
      println("navigated " + page)
      scalajs.js.timers.setTimeout(0)(routerLogic.ctl.set(page.value).runNow())
    }

    // subscribe to navigation changes
    SeqexecCircuit.subscribe(SeqexecCircuit.zoom(_.uiModel.navLocation))(navigated _)

    // Initiate the WebSocket connection
    SeqexecCircuit.dispatch(WSConnect(0))

    router
  }

}
